#include "files.h"
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QProcess>

QString Files::join(const QString& dir, const QString& name) const {
    return dir.endsWith('/') ? dir + name : dir + "/" + name;
}

bool Files::insideRoot(const QString& root, const QString& dir) {
    const QString r = QFileInfo(root).canonicalFilePath();
    const QString d = QFileInfo(dir).canonicalFilePath();
    if (r.isEmpty() || d.isEmpty()) return false;
    return d == r || d.startsWith(r.endsWith('/') ? r : r + "/");
}

QString Files::badName(const QString& name) {
    if (name.trimmed().isEmpty())           return "Give it a name.";
    if (name.contains('/') || name.contains('\0')) return "A name can't contain “/”.";
    if (name == "." || name == "..")        return "That name is reserved.";
    if (name.toUtf8().size() > 255)         return "That name is too long.";
    return "";
}

bool Files::open(const QString& path) {
    // xdg-open resolves the type and the default handler from the image's mimeapps.list.
    // Deliberately not QDesktopServices: under the sandbox marker Qt would route that
    // through the portal's OpenURI, which the ujima portal does not serve.
    return QProcess::startDetached("xdg-open", {path});
}

QString Files::mkdir(const QString& root, const QString& dir, const QString& name) {
    if (const QString e = badName(name); !e.isEmpty()) return e;
    if (!insideRoot(root, dir)) return "That folder isn't in this place.";
    const QDir d(dir);
    if (d.exists(name)) return "Something with that name already exists.";
    return d.mkdir(name) ? "" : "Couldn't create the folder here.";
}

QString Files::rename(const QString& root, const QString& dir, const QString& name, const QString& next) {
    if (const QString e = badName(next); !e.isEmpty()) return e;
    if (!insideRoot(root, dir)) return "That folder isn't in this place.";
    QDir d(dir);
    if (!d.exists(name)) return "That item is gone.";
    if (name == next) return "";
    if (d.exists(next)) return "Something with that name already exists.";
    return d.rename(name, next) ? "" : "Couldn't rename it.";
}

QString Files::remove(const QString& root, const QString& dir, const QString& name) {
    if (const QString e = badName(name); !e.isEmpty()) return e;
    if (!insideRoot(root, dir)) return "That folder isn't in this place.";
    const QString target = join(dir, name);
    const QFileInfo fi(target);
    if (!fi.exists() && !fi.isSymLink()) return "That item is gone.";
    if (fi.isDir() && !fi.isSymLink()) return QDir(target).removeRecursively() ? "" : "Couldn't delete the folder.";
    return QFile::remove(target) ? "" : "Couldn't delete it.";
}
