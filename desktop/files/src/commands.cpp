#include "commands.h"
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkReply>

void Commands::post(const QString& verb, const QVariantMap& params) {
    QNetworkRequest req(QUrl(m_base.toString() + "/" + verb));
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    req.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
    QNetworkReply* reply = m_nam.post(req, QJsonDocument(QJsonObject::fromVariantMap(params)).toJson(QJsonDocument::Compact));
    connect(reply, &QNetworkReply::finished, this, [this, reply, verb, params] {
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        if (status < 200 || status >= 300) emit refused(verb, params, status);
        reply->deleteLater();
    });
}
