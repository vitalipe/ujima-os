#include "transfer.h"
#include "files.h"
#include <QDir>
#include <QDirIterator>
#include <QElapsedTimer>
#include <QFile>
#include <QFileInfo>
#include <QRandomGenerator>
#include <QStorageInfo>
#include <cerrno>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

Transfer::~Transfer() {
    if (m_thread) { m_cancel = true; m_thread->wait(); delete m_thread; }
}

// "name", then "name (2)", "name (3)" … — a file keeps its extension: "photo (2).jpg"
QString Transfer::freeName(const QString& dir, const QString& name, bool isDir) {
    const QDir d(dir);
    if (!d.exists(name)) return name;
    const int dot = name.lastIndexOf('.');
    const bool ext = !isDir && dot > 0;
    const QString stem = ext ? name.left(dot) : name;
    const QString tail = ext ? name.mid(dot) : "";
    for (int n = 2; ; ++n) {
        const QString c = stem + " (" + QString::number(n) + ")" + tail;
        if (!d.exists(c)) return c;
    }
}

qint64 Transfer::sizeOf(const QString& path) {
    const QFileInfo fi(path);
    if (!fi.isDir()) return fi.size();
    qint64 sum = 0;
    QDirIterator it(path, QDir::Files | QDir::Hidden | QDir::System | QDir::NoSymLinks, QDirIterator::Subdirectories);
    while (it.hasNext() && !m_cancel) { it.next(); sum += it.fileInfo().size(); }
    return sum;
}

QString Transfer::put(const QString& root, const QString& dir, const QString& src, bool move, bool sync) {
    if (m_thread) return "Something is still being copied.";
    m_dir = dir; m_move = move; m_cancel = false; m_done = 0; m_total = 0; w_done = 0; w_total = 0;
    m_thread = QThread::create([this, root, src, sync] {
        const Outcome o = run(root, src, sync);
        QMetaObject::invokeMethod(this, [this, o] {
            m_thread = nullptr;
            setStage("");
            emit busyChanged();
            emit finished(o.error, m_dir, o.placed, m_move, o.cancelled);
        }, Qt::QueuedConnection);
    });
    connect(m_thread, &QThread::finished, m_thread, &QObject::deleteLater);
    setStage("copy");
    emit busyChanged();
    emit progressChanged();
    m_thread->start();
    return "";
}

void Transfer::cancel() {
    if (m_stage != "copy") return;
    m_cancel = true;
    setStage("cancel");
}

void Transfer::setStage(const QString& stage) {
    if (stage == m_stage) return;
    m_stage = stage;
    emit stageChanged();
}

// on the worker thread
void Transfer::enter(const QString& stage) {
    QMetaObject::invokeMethod(this, [this, stage] { if (m_thread) setStage(stage); }, Qt::QueuedConnection);
}

void Transfer::report() {
    const qint64 done = w_done, total = w_total;
    QMetaObject::invokeMethod(this, [this, done, total] { m_done = done; m_total = total; emit progressChanged(); },
                              Qt::QueuedConnection);
}

static void syncAt(const QString& dir) {
    const int fd = ::open(QFile::encodeName(dir).constData(), O_RDONLY);
    if (fd >= 0) { ::syncfs(fd); ::close(fd); }
}

// 0, or the errno
static int renameTo(const QString& from, const QString& to) {
    return ::rename(QFile::encodeName(from).constData(), QFile::encodeName(to).constData()) == 0 ? 0 : errno;
}

// on the worker thread
Transfer::Outcome Transfer::run(const QString& root, const QString& src, bool sync) {
    if (!Files::insideRoot(root, m_dir)) return {"That folder isn't in this place."};
    const QFileInfo s(src);
    if (!s.exists() && !s.isSymLink()) return {"That item is gone."};
    const QString from = s.canonicalFilePath(), into = QFileInfo(m_dir).canonicalFilePath();
    if (s.canonicalPath() == into) return {"It's already here."};
    if (s.isDir() && (into == from || into.startsWith(from + "/")))
        return {m_move ? "A folder can't be moved into itself." : "A folder can't be copied into itself."};
    const bool isDir = s.isDir() && !s.isSymLink();
    const QString failed = m_move ? "Couldn't move it here." : "Couldn't copy it here.";

    if (m_move) {
        const QString placed = freeName(into, s.fileName(), s.isDir());
        const int err = renameTo(from, into + "/" + placed);
        if (err == 0) {
            w_done = w_total = 1; report();
            enter("finish");
            if (sync) syncAt(m_dir);
            return {"", placed};
        }
        if (err != EXDEV) return {failed};
    }
    w_total = sizeOf(from);
    report();
    if (m_cancel) return {"", "", true};
    if (QStorageInfo(m_dir).bytesAvailable() < w_total) return {"Not enough space here."};

    const QString part = into + "/.ujima-part-" + QString::number(QRandomGenerator::global()->generate(), 16);
    bool ok = isDir ? copyTree(from, part) : copyFile(from, part);
    QString placed;
    if (ok && !m_cancel) {
        enter("finish");
        placed = freeName(into, s.fileName(), s.isDir());
        ok = renameTo(part, into + "/" + placed) == 0;
    } else {
        ok = false;
    }
    if (!ok) {
        const bool cancelled = placed.isEmpty() && m_cancel;
        if (isDir) QDir(part).removeRecursively(); else QFile::remove(part);
        return cancelled ? Outcome{"", "", true} : Outcome{failed};
    }
    if (sync) syncAt(m_dir);
    if (m_move) {
        const bool gone = isDir ? QDir(from).removeRecursively() : QFile::remove(from);
        if (!gone) return {"Copied, but the original couldn't be removed.", placed};
    }
    return {"", placed};
}

bool Transfer::copyTree(const QString& from, const QString& to) {
    if (!QDir().mkdir(to)) return false;
    const QFileInfoList entries = QDir(from).entryInfoList(QDir::AllEntries | QDir::NoDotAndDotDot | QDir::Hidden | QDir::System);
    for (const QFileInfo& fi : entries) {
        if (m_cancel) return false;
        if (fi.isSymLink()) continue;
        const QString next = to + "/" + fi.fileName();
        if (fi.isDir() ? !copyTree(fi.filePath(), next) : !copyFile(fi.filePath(), next)) return false;
    }
    return true;
}

bool Transfer::copyFile(const QString& from, const QString& to) {
    QFile in(from), out(to);
    if (!in.open(QIODevice::ReadOnly) || !out.open(QIODevice::WriteOnly | QIODevice::Truncate)) return false;
    QByteArray buf(1 << 20, Qt::Uninitialized);
    QElapsedTimer tick; tick.start();
    qint64 held = 0;
    while (!in.atEnd()) {
        if (m_cancel) return false;
        const qint64 n = in.read(buf.data(), buf.size());
        if (n < 0 || out.write(buf.constData(), n) != n) return false;
        w_done += n;
        // sent on as it goes, so the bar is how far the copy really is and a cancel waits for 8 MB, not the file
        if ((held += n) >= (8 << 20)) { out.flush(); ::fdatasync(out.handle()); held = 0; }
        if (tick.elapsed() > 80) { report(); tick.restart(); }
    }
    report();
    return true;
}
