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
public:
    enum Roles { NameRole = Qt::UserRole + 1, TypeRole, MetaRole, IsDirRole, PathRole };
    explicit DirModel(QObject* parent = nullptr);

    int rowCount(const QModelIndex& = {}) const override { return m_rows.size(); }
    QVariant data(const QModelIndex& idx, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    QString path() const { return m_path; }
    void setPath(const QString& p);
    bool hideTokenDir() const { return m_hideToken; }
    void setHideTokenDir(bool v);
    int count() const { return m_rows.size(); }

    Q_INVOKABLE void refresh();
    Q_INVOKABLE int indexOf(const QString& name) const;
    Q_INVOKABLE QVariantMap get(int row) const;

signals:
    void pathChanged();
    void hideTokenDirChanged();
    void countChanged();

private:
    struct Row { QString name, type, meta, path; bool isDir; };
    static QString typeOf(const QFileInfo& fi);
    static QString sizeLabel(qint64 bytes);

    QString m_path;
    bool m_hideToken = false;
    QList<Row> m_rows;
    QFileSystemWatcher m_watch;
    QTimer m_debounce;
};
