#pragma once
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QTimer>
#include <QUrl>

// One of ujimad's NDJSON streams: every line is a whole snapshot. Reconnects a second after the
// stream ends; `connected` is true from the first snapshot until then.
class Stream : public QObject {
    Q_OBJECT
public:
    explicit Stream(const QUrl& url, QObject* parent = nullptr);
    bool connected() const { return m_connected; }

signals:
    void snapshot(const QJsonObject& blob);
    void connectedChanged();

private:
    void open();
    void readLines();
    void ended();

    QUrl m_url;
    QNetworkAccessManager m_nam;
    QNetworkReply* m_reply = nullptr;
    QByteArray m_buf;
    QTimer m_retry;
    bool m_connected = false;
};
