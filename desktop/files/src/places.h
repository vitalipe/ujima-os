#pragma once
#include <QAbstractListModel>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QTimer>
#include <QUrl>

// The places stream (ujimad, NDJSON) as a list model: one row per READY place, the machine's
// Files area first, then sticks. Space figures come from statvfs on each browse root.
struct Place {
    QString id, kind, state, label, fstype, root;
    qint64 used = 0, total = 0;
};

class PlacesModel : public QAbstractListModel {
    Q_OBJECT
    Q_PROPERTY(bool connected READ connected NOTIFY connectedChanged)
    Q_PROPERTY(int count READ count NOTIFY countChanged)
public:
    enum Roles { IdRole = Qt::UserRole + 1, KindRole, StateRole, LabelRole, FstypeRole, RootRole,
                 UsedRole, TotalRole, FreeRole, PctRole };
    explicit PlacesModel(const QUrl& streamUrl, QObject* parent = nullptr);

    int rowCount(const QModelIndex& = {}) const override { return m_places.size(); }
    QVariant data(const QModelIndex& idx, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return m_places.size(); }
    bool connected() const { return m_connected; }
    Q_INVOKABLE QVariantMap get(int row) const;
    Q_INVOKABLE int indexOfId(const QString& id) const;
    Q_INVOKABLE void refreshSpace();

signals:
    void connectedChanged();
    void countChanged();
    void placesChanged();

private:
    void connectStream();
    void readLines();
    void ended();
    void apply(const QJsonArray& places);

    QUrl m_url;
    QNetworkAccessManager m_nam;
    QNetworkReply* m_reply = nullptr;
    QByteArray m_buf;
    QTimer m_retry;
    bool m_connected = false;
    QList<Place> m_places;
};
