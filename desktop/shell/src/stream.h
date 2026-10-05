#pragma once
#include <QByteArray>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QTimer>
#include <QUrl>

// One of ujimad's NDJSON pushes: a snapshot line on connect, then a line per change. A drop (an
// ujimad restart) reconnects a second later, and the fresh snapshot rehydrates the consumer.
class Stream : public QObject {
    Q_OBJECT
public:
    Stream(QNetworkAccessManager& nam, const QUrl& url, QObject* parent = nullptr);

signals:
    void received(const QJsonObject& line);

private:
    void open();
    void readLines();
    void ended();

    QNetworkAccessManager& m_nam;
    QUrl m_url;
    QNetworkReply* m_reply = nullptr;
    QByteArray m_buf;
    QTimer m_retry;
};
