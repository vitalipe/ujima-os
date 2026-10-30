#pragma once
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QStringList>
#include <QTimer>
#include <QUrl>
#include <QVariantList>
#include <QVariantMap>

// ujimad's desktop tier as the shell sees it: the catalog, fetched once (retried until ujimad
// answers), the settings blob off stream/state, the open apps off stream/apps, and the verbs the
// shell fires. App icons and the wall are URLs under the same base — the shell knows no machine
// layout, only where ujimad is (its own glyphs ship beside its QML).
class Desktop : public QObject {
    Q_OBJECT
    Q_PROPERTY(QVariantList catalog    READ catalog    NOTIFY catalogChanged)    // {id, label, category, description, icon}
    Q_PROPERTY(bool         hasCatalog READ hasCatalog NOTIFY catalogChanged)
    Q_PROPERTY(QString      name       READ name       NOTIFY identityChanged)
    Q_PROPERTY(QString      serialTail READ serialTail NOTIFY identityChanged)
    Q_PROPERTY(QVariantMap  audio      READ audio      NOTIFY stateChanged)      // {volume, muted, output}: {value, locked}
    Q_PROPERTY(QVariantMap  keyboard   READ keyboard   NOTIFY stateChanged)      // {layout: {value, locked}, layouts, next}
    Q_PROPERTY(QStringList  open       READ open       NOTIFY openChanged)       // ids with a window
    Q_PROPERTY(QVariantList running    READ running    NOTIFY appsChanged)       // {id, label, category, description, icon, title}
    Q_PROPERTY(QVariantMap  current    READ current    NOTIFY appsChanged)       // the focused app; empty at home
    Q_PROPERTY(QVariantList pinned     READ pinned     NOTIFY appsChanged)       // {id, label, category, description, icon}
    Q_PROPERTY(bool         barsHidden READ barsHidden NOTIFY appsChanged)
    Q_PROPERTY(bool         hasApps    READ hasApps    NOTIFY appsChanged)       // the first snapshot arrived
    Q_PROPERTY(QString      wall       READ wall       CONSTANT)
public:
    explicit Desktop(const QUrl& base, QObject* parent = nullptr);

    QVariantList catalog() const { return m_catalog; }
    bool hasCatalog() const { return m_hasCatalog; }
    QString name() const { return m_name; }
    QString serialTail() const { return m_serialTail; }
    QVariantMap audio() const { return m_audio; }
    QVariantMap keyboard() const { return m_keyboard; }
    QStringList open() const { return m_open; }
    QVariantList running() const { return m_running; }
    QVariantMap current() const { return m_current; }
    QVariantList pinned() const { return m_pinned; }
    bool barsHidden() const { return m_barsHidden; }
    bool hasApps() const { return m_hasApps; }
    QString wall() const { return url("assets/wall.png"); }

    Q_INVOKABLE void openApp(const QString& id);
    Q_INVOKABLE void home();
    Q_INVOKABLE void closeApp();
    Q_INVOKABLE void setMuted(bool muted);
    Q_INVOKABLE void setVolume(int volume);
    Q_INVOKABLE void setLayout(const QString& layout);

signals:
    void catalogChanged();
    void identityChanged();
    void stateChanged();
    void openChanged();
    void appsChanged();

private:
    QString url(const QString& path) const { return m_base + "/" + path; }
    QVariantMap withIcon(const QJsonObject& app) const;
    void fetchCatalog();
    void post(const QString& path, const QJsonObject& body = {});

    QString m_base;
    QNetworkAccessManager m_nam;
    QTimer m_catalogRetry;
    QVariantList m_catalog;
    bool m_hasCatalog = false;
    QString m_name, m_serialTail;
    QVariantMap m_audio, m_keyboard;
    QStringList m_open;
    QVariantList m_running, m_pinned;
    QVariantMap m_current;
    bool m_barsHidden = false;
    bool m_hasApps = false;
};
