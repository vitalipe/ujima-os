#pragma once
#include <QJsonObject>
#include "stream.h"

// This machine as the state stream tells it: the name other computers see it under.
class Machine : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString name READ name NOTIFY nameChanged)
public:
    explicit Machine(const QUrl& stateUrl, QObject* parent = nullptr) : QObject(parent), m_stream(stateUrl) {
        connect(&m_stream, &Stream::snapshot, this, [this](const QJsonObject& blob) {
            const QString name = blob.value("system").toObject().value("name").toString();
            if (name != m_name) { m_name = name; emit nameChanged(); }
        });
    }
    QString name() const { return m_name; }

signals:
    void nameChanged();

private:
    Stream m_stream;
    QString m_name;
};
