#pragma once
#include <QObject>
#include <QThread>
#include <atomic>

// One put at a time, off the UI thread, checks included — a nearby computer that went quiet holds
// each for up to a minute: sizes the source, refuses a target short on space, copies file by file
// reporting bytes, renames when a move stays on one filesystem, syncs a stick when done. A copy is
// written under a hidden name and takes its own when it is whole, so nothing half copied ever
// carries it. put() answers "" or the reason it will not start; finished() carries the outcome.
// Only the copying can be cancelled; a copy that fails or is cancelled takes its partial target
// with it.
class Transfer : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool busy READ busy NOTIFY busyChanged)
    Q_PROPERTY(bool moving READ moving NOTIFY busyChanged)
    Q_PROPERTY(QString dir READ dir NOTIFY busyChanged)
    Q_PROPERTY(QString stage READ stage NOTIFY stageChanged)   // "" | copy | finish | cancel
    Q_PROPERTY(double done READ done NOTIFY progressChanged)
    Q_PROPERTY(double total READ total NOTIFY progressChanged)
public:
    using QObject::QObject;
    ~Transfer() override;
    Q_INVOKABLE QString put(const QString& root, const QString& dir, const QString& src, bool move, bool sync);
    Q_INVOKABLE void cancel();
    bool busy() const { return m_thread != nullptr; }
    bool moving() const { return m_move; }
    QString dir() const { return m_dir; }
    QString stage() const { return m_stage; }
    double done() const { return double(m_done); }
    double total() const { return double(m_total); }
signals:
    void busyChanged();
    void stageChanged();
    void progressChanged();
    void finished(QString error, QString dir, QString placed, bool moved, bool cancelled);
private:
    struct Outcome { QString error, placed; bool cancelled = false; };
    Outcome run(const QString& root, const QString& src, bool sync);
    bool copyTree(const QString& from, const QString& to);
    bool copyFile(const QString& from, const QString& to);
    qint64 sizeOf(const QString& path);
    void report();
    void enter(const QString& stage);
    void setStage(const QString& stage);
    static QString freeName(const QString& dir, const QString& name, bool isDir);

    QThread* m_thread = nullptr;
    QString m_dir, m_stage;
    bool m_move = false;
    std::atomic<bool> m_cancel{false};
    qint64 m_done = 0, m_total = 0;     // the UI thread's copy, set only by report()'s queued call
    qint64 w_done = 0, w_total = 0;     // the worker's own counters
};
