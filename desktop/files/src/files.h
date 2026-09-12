#pragma once
#include <QObject>

// The operations, direct on the filesystem. Every target is canonicalized and refused if it
// leaves its place root; every name must be a plain single segment. "" = ok, else the reason.
class Files : public QObject {
    Q_OBJECT
public:
    using QObject::QObject;
    Q_INVOKABLE bool    open  (const QString& path);
    Q_INVOKABLE QString mkdir (const QString& root, const QString& dir, const QString& name);
    Q_INVOKABLE QString rename(const QString& root, const QString& dir, const QString& name, const QString& next);
    Q_INVOKABLE QString remove(const QString& root, const QString& dir, const QString& name);
    Q_INVOKABLE QString join  (const QString& dir, const QString& name) const;
    Q_INVOKABLE bool    inside(const QString& root, const QString& path) const { return insideRoot(root, path); }
    static bool insideRoot(const QString& root, const QString& dir);
private:
    static QString badName(const QString& name);
};
