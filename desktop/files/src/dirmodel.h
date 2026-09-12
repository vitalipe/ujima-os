#pragma once
#include <QAbstractListModel>
#include <QFileInfo>
#include <QFileSystemWatcher>
#include <QTimer>

// One directory as a list: folders first, then files, locale-aware. Dotfiles are hidden, and
// so is the stick's ujima/ token dir when the model sits at a place root that carries one.
class DirModel : public QAbstractListModel {
    Q_OBJECT
    Q_PROPERTY(QString path READ path WRITE setPath NOTIFY pathChanged)
    Q_PROPERTY(bool hideTokenDir READ hideTokenDir WRITE setHideTokenDir NOTIFY hideTokenDirChanged)
    Q_PROPERTY(int count READ count NOTIFY countChanged)
    Q_PROPERTY(QStringList filter READ filter WRITE setFilter NOTIFY filterChanged)   // globs; empty = every file
public:
    enum Roles { NameRole = Qt::UserRole + 1, TypeRole, MetaRole, IsDirRole, PathRole, SizeRole, MtimeRole };
    explicit DirModel(QObject* parent = nullptr);

    int rowCount(const QModelIndex& = {}) const override { return m_rows.size(); }
    QVariant data(const QModelIndex& idx, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    QString path() const { return m_path; }
    void setPath(const QString& p);
    bool hideTokenDir() const { return m_hideToken; }
    void setHideTokenDir(bool v);
    int count() const { return m_rows.size(); }
    QStringList filter() const { return m_filter; }
    void setFilter(const QStringList& globs);

    Q_INVOKABLE void refresh();
    Q_INVOKABLE int indexOf(const QString& name) const;
    Q_INVOKABLE QVariantMap get(int row) const;

signals:
    void pathChanged();
    void hideTokenDirChanged();
    void countChanged();
    void filterChanged();

private:
    struct Row { QString name, type, meta, path; bool isDir; qint64 size; qint64 mtime; };
    static QString typeOf(const QFileInfo& fi);
    static QString sizeLabel(qint64 bytes);

    QString m_path;
    bool m_hideToken = false;
    QStringList m_filter;
    QList<Row> m_rows;
    QFileSystemWatcher m_watch;
    QTimer m_debounce;
};
