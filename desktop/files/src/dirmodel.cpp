#include "dirmodel.h"
#include <QCollator>
#include <QDir>
#include <QMimeDatabase>
#include <algorithm>

DirModel::DirModel(QObject* parent) : QAbstractListModel(parent) {
    m_debounce.setSingleShot(true);
    m_debounce.setInterval(150);
    connect(&m_debounce, &QTimer::timeout, this, &DirModel::refresh);
    connect(&m_watch, &QFileSystemWatcher::directoryChanged, &m_debounce, qOverload<>(&QTimer::start));
}

void DirModel::setPath(const QString& p) {
    if (p == m_path) return;
    if (!m_path.isEmpty()) m_watch.removePath(m_path);
    m_path = p;
    if (!m_path.isEmpty()) m_watch.addPath(m_path);
    emit pathChanged();
    refresh();
}

void DirModel::setHideTokenDir(bool v) {
    if (v == m_hideToken) return;
    m_hideToken = v;
    emit hideTokenDirChanged();
    refresh();
}

QString DirModel::typeOf(const QFileInfo& fi) {
    if (fi.isDir()) return "folder";
    static QMimeDatabase db;
    const QString mime = db.mimeTypeForFile(fi, QMimeDatabase::MatchExtension).name();
    if (mime == "application/pdf")   return "pdf";
    if (mime.startsWith("image/"))   return "image";
    if (mime.startsWith("video/"))   return "video";
    if (mime.startsWith("audio/"))   return "audio";
    if (mime.startsWith("text/"))    return "text";
    return "file";
}

QString DirModel::sizeLabel(qint64 b) {
    const double kb = 1000.0, mb = kb * 1000, gb = mb * 1000;
    auto one = [](double v) { QString s = QString::number(v, 'f', 1); if (s.endsWith(".0")) s.chop(2); return s; };
    if (b >= gb) return one(b / gb) + " GB";
    if (b >= 10 * mb) return QString::number(qRound(b / mb)) + " MB";
    if (b >= mb) return one(b / mb) + " MB";
    return QString::number(std::max<qint64>(1, qRound(b / kb))) + " KB";
}

void DirModel::refresh() {
    QList<Row> next;
    if (!m_path.isEmpty()) {
        const QDir dir(m_path);
        const QFileInfoList entries = dir.entryInfoList(QDir::AllEntries | QDir::NoDotAndDotDot, QDir::NoSort);
        for (const QFileInfo& fi : entries) {
            if (m_hideToken && fi.isDir() && fi.fileName() == "ujima") continue;
            Row r;
            r.name  = fi.fileName();
            r.isDir = fi.isDir();
            r.type  = typeOf(fi);
            r.path  = fi.filePath();
            if (r.isDir) {
                const int n = QDir(fi.filePath()).entryList(QDir::AllEntries | QDir::NoDotAndDotDot).size();
                r.meta = QString::number(n) + (n == 1 ? " item" : " items");
            } else {
                r.meta = sizeLabel(fi.size());
            }
            next.push_back(r);
        }
        QCollator coll;
        coll.setNumericMode(true);
        coll.setCaseSensitivity(Qt::CaseInsensitive);
        std::stable_sort(next.begin(), next.end(), [&](const Row& a, const Row& b) {
            if (a.isDir != b.isDir) return a.isDir;
            return coll.compare(a.name, b.name) < 0;
        });
    }
    beginResetModel();
    m_rows = next;
    endResetModel();
    emit countChanged();
}

QVariant DirModel::data(const QModelIndex& idx, int role) const {
    if (!idx.isValid() || idx.row() >= m_rows.size()) return {};
    const Row& r = m_rows[idx.row()];
    switch (role) {
    case NameRole:  return r.name;
    case TypeRole:  return r.type;
    case MetaRole:  return r.meta;
    case IsDirRole: return r.isDir;
    case PathRole:  return r.path;
    }
    return {};
}

QHash<int, QByteArray> DirModel::roleNames() const {
    return {{NameRole, "name"}, {TypeRole, "type"}, {MetaRole, "meta"}, {IsDirRole, "isDir"}, {PathRole, "path"}};
}

int DirModel::indexOf(const QString& name) const {
    for (int i = 0; i < m_rows.size(); ++i) if (m_rows[i].name == name) return i;
    return -1;
}

QVariantMap DirModel::get(int row) const {
    QVariantMap m;
    if (row < 0 || row >= m_rows.size()) return m;
    const Row& r = m_rows[row];
    m["name"] = r.name; m["type"] = r.type; m["meta"] = r.meta; m["isDir"] = r.isDir; m["path"] = r.path;
    return m;
}
