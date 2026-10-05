#include "desktop.h"
#include "stream.h"
#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QNetworkRequest>

Desktop::Desktop(const QUrl& base, QObject* parent) : QObject(parent), m_base(base.toString()) {
    while (m_base.endsWith('/')) m_base.chop(1);

    m_catalogRetry.setSingleShot(true);
    m_catalogRetry.setInterval(250);
    connect(&m_catalogRetry, &QTimer::timeout, this, &Desktop::fetchCatalog);
    fetchCatalog();

    // the settings blob: the identity line (display name + serial tail, absent off-Pi), the
    // sound and the keyboard — every push is whole, so it is simply reapplied
    auto* state = new Stream(m_nam, QUrl(url("stream/state")), this);
    connect(state, &Stream::received, this, [this](const QJsonObject& o) {
        const QJsonObject sys = o.value("system").toObject();
        if (!sys.isEmpty()) {
            m_name       = sys.value("name").toString();
            m_serialTail = sys.value("serialTail").toString();
            emit identityChanged();
        }
        m_audio    = o.value("audio").toObject().toVariantMap();
        m_keyboard = o.value("keyboard").toObject().toVariantMap();
        emit stateChanged();
    });

    // the open apps: every push carries the FULL picture (running[] is populated even at home)
    auto* apps = new Stream(m_nam, QUrl(url("stream/apps")), this);
    connect(apps, &Stream::received, this, [this](const QJsonObject& o) {
        QVariantList running, pinned;
        QStringList open;
        for (const QJsonValue& a : o.value("running").toArray()) {
            running << withIcon(a.toObject());
            open << a.toObject().value("id").toString();
        }
        for (const QJsonValue& p : o.value("pinned").toArray()) pinned << withIcon(p.toObject());
        const QJsonValue current = o.value("current");
        m_running    = running;
        m_pinned     = pinned;
        m_current    = current.isObject() ? withIcon(current.toObject()) : QVariantMap();
        m_barsHidden = o.value("barsHidden").toBool();
        m_hasApps    = true;
        emit appsChanged();
        if (open != m_open) { m_open = open; emit openChanged(); }
    });
}

// the wire names an icon by path on the machine's disk; the shell asks for it by id instead
QVariantMap Desktop::withIcon(const QJsonObject& app) const {
    QVariantMap m = app.toVariantMap();
    m["icon"] = url("assets/app-icon/" + app.value("id").toString());
    return m;
}

// the catalog is immutable for the session: one fetch, retried until ujimad is up
void Desktop::fetchCatalog() {
    QNetworkReply* reply = m_nam.get(QNetworkRequest(QUrl(url("app/catalog"))));
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) { m_catalogRetry.start(); return; }
        QVariantList apps;
        for (const QJsonValue& v : QJsonDocument::fromJson(reply->readAll()).object().value("apps").toArray()) {
            const QJsonObject a = v.toObject();
            const QString id = a.value("id").toString();
            apps << QVariantMap{{"id",       id},
                                {"label",    a.value("label").toString(id)},
                                {"category", a.value("category").toString()},
                                {"icon",     url("assets/app-icon/" + id)}};
        }
        m_catalog = apps;
        m_hasCatalog = true;
        emit catalogChanged();
    });
}

// every verb is fire-and-forget: the 202 is not waited on, the next push shows the outcome
void Desktop::post(const QString& path, const QJsonObject& body) {
    QNetworkRequest req(QUrl(url(path)));
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    QNetworkReply* reply = m_nam.post(req, QJsonDocument(body).toJson(QJsonDocument::Compact));
    connect(reply, &QNetworkReply::finished, reply, &QObject::deleteLater);
}

void Desktop::openApp(const QString& id)        { post("commands/app/open", {{"app", id}}); }
void Desktop::home()                            { post("commands/app/home"); }
void Desktop::closeApp()                        { post("commands/app/close"); }
void Desktop::setMuted(bool muted)              { post("commands/audio/muted", {{"value", muted}}); }
void Desktop::setVolume(int volume)             { post("commands/audio/volume", {{"value", volume}}); }
void Desktop::setLayout(const QString& layout)  { post("commands/keyboard/layout", {{"layout", layout}}); }
