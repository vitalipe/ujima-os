#pragma once
#include <QAbstractListModel>
#include <QFileInfo>
#include <QFileSystemWatcher>
#include <QThreadPool>
#include <QTimer>

// One directory as a list: folders first, then files, locale-aware. Dotfiles are hidden, and
// so is the stick's ujima/ token dir when the model sits at a place root that carries one.
// The directory is read off the UI thread — a nearby computer that went quiet stalls a read for
// up to a minute — and the rows land when it is done; only the latest read asked for lands.
// `watch` off keeps the watcher away too: adding a watch is a lookup on the UI thread.
class DirModel : public QAbstractListModel {
    Q_OBJECT
    Q_PROPERTY(QString path READ path WRITE setPath NOTIFY pathChanged)
    Q_PROPERTY(bool hideTokenDir READ hideTokenDir WRITE setHideTokenDir NOTIFY hideTokenDirChanged)
    Q_PROPERTY(bool watch READ watch WRITE setWatch NOTIFY watchChanged)   // set before path
    Q_PROPERTY(int count READ count NOTIFY countChanged)
    Q_PROPERTY(bool loading READ loading NOTIFY loadingChanged)   // a folder was entered and its rows have not landed
    Q_PROPERTY(QStringList filter READ filter WRITE setFilter NOTIFY filterChanged)   // globs; empty = every file
    Q_PROPERTY(int lead READ lead WRITE setLead NOTIFY leadChanged)   // cells kept free at the front for the view's own tile
public:
    enum Roles { NameRole = Qt::UserRole + 1, TypeRole, MetaRole, IsDirRole, PathRole, SizeRole, MtimeRole };
    explicit DirModel(QObject* parent = nullptr);

    int rowCount(const QModelIndex& = {}) const override { return m_lead + m_rows.size(); }
    QVariant data(const QModelIndex& idx, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    QString path() const { return m_path; }
    void setPath(const QString& p);
    bool hideTokenDir() const { return m_hideToken; }
    void setHideTokenDir(bool v);
    bool watch() const { return m_watching; }
    void setWatch(bool v) { if (v != m_watching) { m_watching = v; emit watchChanged(); } }
    int count() const { return m_rows.size(); }
    bool loading() const { return m_loading; }
    QStringList filter() const { return m_filter; }
    void setFilter(const QStringList& globs);
    int lead() const { return m_lead; }
    void setLead(int n);

    Q_INVOKABLE void refresh();
    Q_INVOKABLE int indexOf(const QString& name) const;
    Q_INVOKABLE QVariantMap get(int row) const;

signals:
    void pathChanged();
    void hideTokenDirChanged();
    void watchChanged();
    void countChanged();
    void loadingChanged();
    void filterChanged();
    void leadChanged();

private:
    struct Row { QString name, type, meta, path; bool isDir; qint64 size; qint64 mtime; };
    static QString typeOf(const QFileInfo& fi);
    static QString sizeLabel(qint64 bytes);
    static QList<Row> read(const QString& path, bool hideToken, const QStringList& filter);
    void land(quint64 gen, const QList<Row>& rows);
    void show(const QList<Row>& rows);
    void setLoading(bool v);

    QString m_path;
    bool m_hideToken = false;
    bool m_watching = true;
    int m_lead = 0;
    QStringList m_filter;
    QList<Row> m_rows;
    bool m_loading = false;
    quint64 m_gen = 0;
    QFileSystemWatcher m_watch;
    QTimer m_debounce;
    QThreadPool m_pool;   // last: destroyed first, and that waits for a read still running
};
