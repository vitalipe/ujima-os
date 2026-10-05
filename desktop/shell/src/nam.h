#pragma once
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QQmlNetworkAccessManagerFactory>

// The engine's network access, for the QML Images. Qt Quick's image loader asks for HTTP
// pipelining on every fetch, and ujimad (http-kit) answers pipelined requests as each one
// completes, not in the order they arrived — so two icons queued on one connection can come
// back swapped and two tiles wear each other's face (seen on HW: 4 of 6 cold starts). Every
// request the engine makes goes out with pipelining off.
class PlainHttp : public QNetworkAccessManager {
public:
    using QNetworkAccessManager::QNetworkAccessManager;
protected:
    QNetworkReply* createRequest(Operation op, const QNetworkRequest& req, QIODevice* data = nullptr) override {
        QNetworkRequest r(req);
        r.setAttribute(QNetworkRequest::HttpPipeliningAllowedAttribute, false);
        return QNetworkAccessManager::createRequest(op, r, data);
    }
};

class PlainHttpFactory : public QQmlNetworkAccessManagerFactory {
public:
    QNetworkAccessManager* create(QObject* parent) override { return new PlainHttp(parent); }
};
