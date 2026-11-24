#pragma once
#include <QHash>
#include <QJSValue>
#include <QObject>
#include <QThreadPool>
#include <functional>

// The operations, direct on the filesystem. Every target is canonicalized and refused if it
// leaves its place root; every name must be a plain single segment. mkdir, rename and remove run
// off the UI thread — a nearby computer that went quiet holds each call for up to a minute — and
// THEN is called with "" or the reason.
class Files : public QObject {
    Q_OBJECT
public:
    using QObject::QObject;
    Q_INVOKABLE bool    open  (const QString& path);
    Q_INVOKABLE void    mkdir (const QString& root, const QString& dir, const QString& name, const QJSValue& then);
    Q_INVOKABLE void    rename(const QString& root, const QString& dir, const QString& name, const QString& next, const QJSValue& then);
    Q_INVOKABLE void    remove(const QString& root, const QString& dir, const QString& name, const QJSValue& then);
    Q_INVOKABLE QString join  (const QString& dir, const QString& name) const;
    Q_INVOKABLE bool    inside(const QString& root, const QString& path) const { return insideRoot(root, path); }
    static bool insideRoot(const QString& root, const QString& dir);
private:
    static QString badName(const QString& name);
    static QString makeDir(const QString& root, const QString& dir, const QString& name);
    static QString moveTo (const QString& root, const QString& dir, const QString& name, const QString& next);
    static QString erase  (const QString& root, const QString& dir, const QString& name);
    void run(std::function<QString()> work, const QJSValue& then);

    quint64 m_seq = 0;
    QHash<quint64, QJSValue> m_then;   // the callbacks stay on the UI thread
    QThreadPool m_pool;                // last: destroyed first, and that waits for a call still running
};
