#include "places.h"
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QStorageInfo>
#include <algorithm>

PlacesModel::PlacesModel(const QUrl& streamUrl, QObject* parent)
    : QAbstractListModel(parent), m_url(streamUrl) {
    m_retry.setSingleShot(true);
    m_retry.setInterval(1000);
    connect(&m_retry, &QTimer::timeout, this, &PlacesModel::connectStream);
    connectStream();
}

void PlacesModel::connectStream() {
    if (m_reply) { m_reply->deleteLater(); m_reply = nullptr; }
    m_buf.clear();
    QNetworkRequest req(m_url);
    req.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
    m_reply = m_nam.get(req);
    connect(m_reply, &QNetworkReply::readyRead, this, &PlacesModel::readLines);
    connect(m_reply, &QNetworkReply::finished,  this, &PlacesModel::ended);
}

void PlacesModel::readLines() {
    m_buf += m_reply->readAll();
    int nl;
    while ((nl = m_buf.indexOf('\n')) >= 0) {
        const QByteArray line = m_buf.left(nl).trimmed();
        m_buf.remove(0, nl + 1);
        if (line.isEmpty()) continue;
        const QJsonDocument doc = QJsonDocument::fromJson(line);
        if (!doc.isObject()) continue;
        if (!m_connected) { m_connected = true; emit connectedChanged(); }
        apply(doc.object().value("places").toArray());
    }
}

void PlacesModel::ended() {
    if (m_connected) { m_connected = false; emit connectedChanged(); }
    m_retry.start();
}

static qint64 usedOf(const QString& root, qint64* total) {
    QStorageInfo si(root);
    *total = si.bytesTotal();
    return si.bytesTotal() - si.bytesAvailable();
}

void PlacesModel::apply(const QJsonArray& places) {
    QList<Place> next;
    for (const QJsonValue& v : places) {
        const QJsonObject o = v.toObject();
        if (o.value("state").toString() != "ready") continue;
        Place p;
        const QJsonArray id = o.value("id").toArray();
        p.kind   = o.value("kind").toString();
        p.id     = p.kind + "/" + id.at(1).toString();
        p.state  = o.value("state").toString();
        p.name   = o.value("name").toString();
        p.label  = o.value("label").toString();
        p.fstype = o.value("fstype").toString();
        p.root   = o.value("storage").toString();
        for (const QJsonValue& t : o.value("tokens").toArray()) p.tokens << t.toString();
        while (p.root.size() > 1 && p.root.endsWith('/')) p.root.chop(1);
        p.used   = usedOf(p.root, &p.total);
        next.push_back(p);
    }
    // the design's order: the session first, then this computer, then sticks by name
    auto rank = [](const QString& kind) { return kind == "session" ? 0 : kind == "local" ? 1 : 2; };
    std::stable_sort(next.begin(), next.end(), [&](const Place& a, const Place& b) {
        if (rank(a.kind) != rank(b.kind)) return rank(a.kind) < rank(b.kind);
        return a.name.localeAwareCompare(b.name) < 0;
    });
    beginResetModel();
    m_places = next;
    endResetModel();
    emit countChanged();
    emit placesChanged();
}

void PlacesModel::refreshSpace() {
    for (Place& p : m_places) p.used = usedOf(p.root, &p.total);
    if (!m_places.isEmpty())
        emit dataChanged(index(0), index(m_places.size() - 1), {UsedRole, TotalRole, FreeRole, PctRole});
}

QVariant PlacesModel::data(const QModelIndex& idx, int role) const {
    if (!idx.isValid() || idx.row() >= m_places.size()) return {};
    const Place& p = m_places[idx.row()];
    switch (role) {
    case IdRole:     return p.id;
    case KindRole:   return p.kind;
    case StateRole:  return p.state;
    case NameRole:   return p.name;
    case LabelRole:  return p.label;
    case FstypeRole: return p.fstype;
    case RootRole:   return p.root;
    case UsedRole:   return double(p.used);
    case TotalRole:  return double(p.total);
    case FreeRole:   return double(std::max<qint64>(0, p.total - p.used));
    case PctRole:    return p.total > 0 ? int(std::min<qint64>(100, (p.used * 100) / p.total)) : 0;
    case TokensRole: return p.tokens;
    }
    return {};
}

QHash<int, QByteArray> PlacesModel::roleNames() const {
    return {{IdRole, "id"}, {KindRole, "kind"}, {StateRole, "state"}, {NameRole, "name"},
            {LabelRole, "label"}, {FstypeRole, "fstype"}, {RootRole, "root"}, {UsedRole, "used"},
            {TotalRole, "total"}, {FreeRole, "free"}, {PctRole, "pct"}, {TokensRole, "tokens"}};
}

QVariantMap PlacesModel::get(int row) const {
    QVariantMap m;
    if (row < 0 || row >= m_places.size()) return m;
    const auto names = roleNames();
    for (auto it = names.cbegin(); it != names.cend(); ++it) m.insert(QString::fromUtf8(it.value()), data(index(row), it.key()));
    return m;
}

int PlacesModel::indexOfId(const QString& id) const {
    for (int i = 0; i < m_places.size(); ++i) if (m_places[i].id == id) return i;
    return -1;
}
