#include "transfer.h"
#include "files.h"
#include <QDir>
#include <QDirIterator>
#include <QElapsedTimer>
#include <QFile>
#include <QFileInfo>
#include <QStorageInfo>
#include <cerrno>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>

Transfer::~Transfer() {
    if (m_thread) { m_thread->wait(); delete m_thread; }
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
    while (it.hasNext()) { it.next(); sum += it.fileInfo().size(); }
    return sum;
}

QString Transfer::put(const QString& root, const QString& dir, const QString& src, bool move, bool sync) {
    if (m_thread) return "Something is still being copied.";
    if (!Files::insideRoot(root, dir)) return "That folder isn't in this place.";
    const QFileInfo s(src);
    if (!s.exists() && !s.isSymLink()) return "That item is gone.";
    const QString from = s.canonicalFilePath(), into = QFileInfo(dir).canonicalFilePath();
    if (s.canonicalPath() == into) return "It's already here.";
    if (s.isDir() && (into == from || into.startsWith(from + "/")))
        return move ? "A folder can't be moved into itself." : "A folder can't be copied into itself.";
    const QString placed = freeName(into, s.fileName(), s.isDir());
    const QString to = into + "/" + placed;
    const bool isDir = s.isDir() && !s.isSymLink();

    m_dir = dir; m_move = move; m_done = 0; m_total = 0; w_done = 0; w_total = 0;
    m_thread = QThread::create([this, from, to, isDir, sync, placed, move] {
        const QString err = run(from, to, isDir, sync);
        QMetaObject::invokeMethod(this, [this, err, placed, move] {
            m_thread = nullptr;
            emit busyChanged();
            emit finished(err, m_dir, placed, move);
        }, Qt::QueuedConnection);
    });
    connect(m_thread, &QThread::finished, m_thread, &QObject::deleteLater);
    emit busyChanged();
    emit progressChanged();
    m_thread->start();
    return "";
}

void Transfer::report() {
    const qint64 done = w_done, total = w_total;
    QMetaObject::invokeMethod(this, [this, done, total] { m_done = done; m_total = total; emit progressChanged(); },
                              Qt::QueuedConnection);
}

// on the worker thread
QString Transfer::run(const QString& from, const QString& to, bool isDir, bool sync) {
    if (m_move) {
        if (::rename(QFile::encodeName(from).constData(), QFile::encodeName(to).constData()) == 0) {
            w_done = w_total = 1; report();
            if (sync) { const int fd = ::open(QFile::encodeName(m_dir).constData(), O_RDONLY); if (fd >= 0) { ::syncfs(fd); ::close(fd); } }
            return "";
        }
        if (errno != EXDEV) return "Couldn't move it here.";
    }
    w_total = sizeOf(from);
    report();
    if (QStorageInfo(m_dir).bytesAvailable() < w_total) return "Not enough space here.";
    const bool ok = isDir ? copyTree(from, to) : copyFile(from, to);
    if (!ok) {
        if (isDir) QDir(to).removeRecursively(); else QFile::remove(to);
        return m_move ? "Couldn't move it here." : "Couldn't copy it here.";
    }
    if (sync) { const int fd = ::open(QFile::encodeName(m_dir).constData(), O_RDONLY); if (fd >= 0) { ::syncfs(fd); ::close(fd); } }
    if (m_move) {
        const bool gone = isDir ? QDir(from).removeRecursively() : QFile::remove(from);
        if (!gone) return "Copied, but the original couldn't be removed.";
    }
    return "";
}

bool Transfer::copyTree(const QString& from, const QString& to) {
    if (!QDir().mkdir(to)) return false;
    const QFileInfoList entries = QDir(from).entryInfoList(QDir::AllEntries | QDir::NoDotAndDotDot | QDir::Hidden | QDir::System);
    for (const QFileInfo& fi : entries) {
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
    while (!in.atEnd()) {
        const qint64 n = in.read(buf.data(), buf.size());
        if (n < 0 || out.write(buf.constData(), n) != n) return false;
        w_done += n;
        if (tick.elapsed() > 80) { report(); tick.restart(); }
    }
    report();
    return true;
}
