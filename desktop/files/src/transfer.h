#pragma once
#include <QObject>
#include <QThread>

// One put at a time, off the UI thread: sizes the source, refuses a target short on space, copies
// file by file reporting bytes, renames when a move stays on one filesystem, syncs a stick when
// done. put() answers "" or the reason it will not start; finished() carries the outcome. A copy
// that fails takes its partial target with it.
class Transfer : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool busy READ busy NOTIFY busyChanged)
    Q_PROPERTY(bool moving READ moving NOTIFY busyChanged)
    Q_PROPERTY(QString dir READ dir NOTIFY busyChanged)
    Q_PROPERTY(double done READ done NOTIFY progressChanged)
    Q_PROPERTY(double total READ total NOTIFY progressChanged)
public:
    using QObject::QObject;
    ~Transfer() override;
    Q_INVOKABLE QString put(const QString& root, const QString& dir, const QString& src, bool move, bool sync);
    bool busy() const { return m_thread != nullptr; }
    bool moving() const { return m_move; }
    QString dir() const { return m_dir; }
    double done() const { return double(m_done); }
    double total() const { return double(m_total); }

signals:
    void busyChanged();
    void progressChanged();
    void finished(QString error, QString dir, QString placed, bool moved);

private:
    QString run(const QString& from, const QString& to, bool isDir, bool sync);
    bool copyTree(const QString& from, const QString& to);
    bool copyFile(const QString& from, const QString& to);
    void report();
    static QString freeName(const QString& dir, const QString& name, bool isDir);
    static qint64 sizeOf(const QString& path);

    QThread* m_thread = nullptr;
    QString m_dir;
    bool m_move = false;
    qint64 m_done = 0, m_total = 0;     // the UI thread's copy, set only by report()'s queued call
    qint64 w_done = 0, w_total = 0;     // the worker's own counters
};
