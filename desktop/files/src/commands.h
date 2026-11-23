#pragma once
#include <QNetworkAccessManager>
#include <QObject>
#include <QUrl>
#include <QVariantMap>

// ujimad's desktop verbs: POST <base>/<verb> with JSON params. A verb that works says nothing —
// its outcome arrives on the places stream; one that is refused, or never answered, says so here.
class Commands : public QObject {
    Q_OBJECT
public:
    explicit Commands(const QUrl& base, QObject* parent = nullptr) : QObject(parent), m_base(base) {}
    Q_INVOKABLE void post(const QString& verb, const QVariantMap& params);

signals:
    void refused(const QString& verb, const QVariantMap& params, int status);   // status 0 = no answer

private:
    QUrl m_base;
    QNetworkAccessManager m_nam;
};
