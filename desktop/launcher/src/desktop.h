#pragma once
#include <QNetworkAccessManager>
#include <QStringList>
#include <QTimer>
#include <QUrl>
#include <QVariantList>

// ujimad's desktop tier as the launcher sees it: the catalog, fetched once (retried until ujimad
// answers), the identity line off stream/state, the open apps off stream/apps, and the one verb
// a tile fires. Icons and the wall are URLs under the same base — the launcher knows no
// filesystem layout, only where ujimad is.
class Desktop : public QObject {
    Q_OBJECT
    Q_PROPERTY(QVariantList apps       READ apps       NOTIFY appsChanged)       // {id, label, category, icon}
    Q_PROPERTY(bool         hasCatalog READ hasCatalog NOTIFY appsChanged)
    Q_PROPERTY(QString      name       READ name       NOTIFY identityChanged)
    Q_PROPERTY(QString      serialTail READ serialTail NOTIFY identityChanged)
    Q_PROPERTY(QStringList  open       READ open       NOTIFY openChanged)       // ids with a window
    Q_PROPERTY(QString      wall       READ wall       CONSTANT)
public:
    explicit Desktop(const QUrl& base, QObject* parent = nullptr);

    QVariantList apps() const { return m_apps; }
    bool hasCatalog() const { return m_hasCatalog; }
    QString name() const { return m_name; }
    QString serialTail() const { return m_serialTail; }
    QStringList open() const { return m_open; }
    QString wall() const { return url("assets/wall.png"); }

    Q_INVOKABLE void openApp(const QString& id);

signals:
    void appsChanged();
    void identityChanged();
    void openChanged();

private:
    QString url(const QString& path) const { return m_base + "/" + path; }
    void fetchCatalog();

    QString m_base;
    QNetworkAccessManager m_nam;
    QTimer m_catalogRetry;
    QVariantList m_apps;
    bool m_hasCatalog = false;
    QString m_name, m_serialTail;
    QStringList m_open;
};
