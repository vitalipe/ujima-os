#pragma once
#include <QAbstractListModel>
#include <QJsonArray>
#include <QUrl>
#include "stream.h"

// The places stream (ujimad, NDJSON) as a list model: one row per READY place, Temporary first,
// then the machine, then sticks. Names come from the wire — the same ones every dialog shows. Space figures come from statvfs on each browse root.
// A nearby computer's shares are not rows: they ride `nearby`, one entry per computer, in every state.
// What this machine shares rides `shared`, each folder with the place it is in.
struct Place {
    QString id, wire, kind, state, name, label, fstype, root;   // name = the display name, from the wire; wire = the id's name there
    QVariantList shares;       // [{name, path, access}]
    QStringList tokens;        // token TYPES the stick carries ("circle/secret", "ujima/pack") — never values
    qint64 used = 0, total = 0;
};

struct PeerShare {
    QString id, wire, label, host, hostId, access, state, root, reason;   // wire = the id's name on the wire, "<machine id>/<share>"
    QVariantMap toMap() const;
};

class PlacesModel : public QAbstractListModel {
    Q_OBJECT
    Q_PROPERTY(bool connected READ connected NOTIFY connectedChanged)
    Q_PROPERTY(int count READ count NOTIFY countChanged)
    Q_PROPERTY(QVariantList nearby READ nearby NOTIFY nearbyChanged)     // [{id, name, rw, shares: [share]}] by machine id
    Q_PROPERTY(int nearbyCount READ nearbyCount NOTIFY nearbyChanged)
    Q_PROPERTY(QVariantList shared READ shared NOTIFY sharedChanged)     // [{place, kind, wire, placeName, name, path, access}] by name
public:
    enum Roles { IdRole = Qt::UserRole + 1, KindRole, StateRole, LabelRole, FstypeRole, RootRole,
                 NameRole, UsedRole, TotalRole, FreeRole, PctRole, TokensRole, WireRole };
    explicit PlacesModel(const QUrl& streamUrl, QObject* parent = nullptr);

    int rowCount(const QModelIndex& = {}) const override { return m_places.size(); }
    QVariant data(const QModelIndex& idx, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return m_places.size(); }
    bool connected() const { return m_stream.connected(); }
    Q_INVOKABLE QVariantMap get(int row) const;
    Q_INVOKABLE int indexOfId(const QString& id) const;
    QVariantList nearby() const { return m_nearby; }
    int nearbyCount() const { return m_peers.size(); }
    QVariantList shared() const { return m_shared; }
    Q_INVOKABLE QVariantMap peer(const QString& id) const;       // a nearby share by place id; empty when gone
    Q_INVOKABLE bool hasReady(const QString& id) const;          // a row, or a nearby share that is mounted
    Q_INVOKABLE void refreshSpace();

signals:
    void connectedChanged();
    void countChanged();
    void nearbyChanged();
    void sharedChanged();
    void placesChanged();

private:
    void apply(const QJsonArray& places);

    Stream m_stream;
    QList<Place> m_places;
    QList<PeerShare> m_peers;
    QVariantList m_nearby;
    QVariantList m_shared;
};
