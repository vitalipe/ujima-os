#include "stream.h"
#include <QJsonDocument>

Stream::Stream(QNetworkAccessManager& nam, const QUrl& url, QObject* parent)
    : QObject(parent), m_nam(nam), m_url(url) {
    m_retry.setSingleShot(true);
    m_retry.setInterval(1000);
    connect(&m_retry, &QTimer::timeout, this, &Stream::open);
    open();
}

void Stream::open() {
    if (m_reply) { m_reply->deleteLater(); m_reply = nullptr; }
    m_buf.clear();
    QNetworkRequest req(m_url);
    req.setAttribute(QNetworkRequest::Http2AllowedAttribute, false);
    m_reply = m_nam.get(req);
    connect(m_reply, &QNetworkReply::readyRead, this, &Stream::readLines);
    connect(m_reply, &QNetworkReply::finished,  this, &Stream::ended);
}

void Stream::readLines() {
    m_buf += m_reply->readAll();
    int nl;
    while ((nl = m_buf.indexOf('\n')) >= 0) {
        const QByteArray line = m_buf.left(nl).trimmed();
        m_buf.remove(0, nl + 1);
        if (line.isEmpty()) continue;
        const QJsonDocument doc = QJsonDocument::fromJson(line);
        if (doc.isObject()) emit received(doc.object());
    }
}

void Stream::ended() { m_retry.start(); }
