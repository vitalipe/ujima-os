#include "desktop.h"
#include "stream.h"
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkReply>
#include <QNetworkRequest>

Desktop::Desktop(const QUrl& base, QObject* parent) : QObject(parent), m_base(base.toString()) {
    while (m_base.endsWith('/')) m_base.chop(1);

    m_catalogRetry.setSingleShot(true);
    m_catalogRetry.setInterval(1000);
    connect(&m_catalogRetry, &QTimer::timeout, this, &Desktop::fetchCatalog);
    fetchCatalog();

    // the identity line: the machine's display name from the settings plane, plus the serial
    // tail (absent off-Pi — the field just stays empty)
    auto* state = new Stream(m_nam, QUrl(url("stream/state")), this);
    connect(state, &Stream::received, this, [this](const QJsonObject& o) {
        const QJsonObject sys = o.value("system").toObject();
        if (sys.isEmpty()) return;
        m_name       = sys.value("name").toString();
        m_serialTail = sys.value("serialTail").toString();
        emit identityChanged();
    });

    // the open apps: every push carries the FULL list (running[] — not a delta, populated even
    // at home), so it is simply reapplied
    auto* apps = new Stream(m_nam, QUrl(url("stream/apps")), this);
    connect(apps, &Stream::received, this, [this](const QJsonObject& o) {
        QStringList open;
        for (const QJsonValue& a : o.value("running").toArray()) open << a.toObject().value("id").toString();
        if (open == m_open) return;
        m_open = open;
        emit openChanged();
    });
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
        m_apps = apps;
        m_hasCatalog = true;
        emit appsChanged();
    });
}

// a click = the same verb the dock fires; fire-and-forget, the 202 is not waited on
void Desktop::openApp(const QString& id) {
    QNetworkRequest req(QUrl(url("commands/app/open")));
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    QNetworkReply* reply = m_nam.post(req, QJsonDocument(QJsonObject{{"app", id}}).toJson(QJsonDocument::Compact));
    connect(reply, &QNetworkReply::finished, reply, &QObject::deleteLater);
}
