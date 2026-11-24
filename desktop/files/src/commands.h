#pragma once
#include <QJSValue>
#include <QNetworkAccessManager>
#include <QObject>
#include <QUrl>
#include <QVariantMap>

// ujimad's desktop verbs: POST <base>/<verb> with JSON params. What a verb changed arrives on the
// places stream; THEN, when given, is called with the HTTP status — 0 when nothing answered.
class Commands : public QObject {
    Q_OBJECT
public:
    explicit Commands(const QUrl& base, QObject* parent = nullptr) : QObject(parent), m_base(base) {}
    Q_INVOKABLE void post(const QString& verb, const QVariantMap& params, const QJSValue& then = QJSValue());

private:
    QUrl m_base;
    QNetworkAccessManager m_nam;
};
