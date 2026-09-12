#pragma once
#include <QObject>
#include <QStringList>
#include <QVariantList>

// A portal file dialog: the request the portal wrote (its build_request JSON), and the
// answer this process prints on stdout before it exits — {"status":"picked","paths":[…]}
// or {"status":"cancelled"}. Paths are absolute, each checked against its place root.
class Picker : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString mode          READ mode          CONSTANT)   // open | save
    Q_PROPERTY(QString title         READ title         CONSTANT)
    Q_PROPERTY(QString acceptLabel   READ acceptLabel   CONSTANT)
    Q_PROPERTY(bool    multiple      READ multiple      CONSTANT)
    Q_PROPERTY(bool    directory     READ directory     CONSTANT)
    Q_PROPERTY(QString currentName   READ currentName   CONSTANT)
    Q_PROPERTY(QString currentFolder READ currentFolder CONSTANT)
    Q_PROPERTY(QVariantList filters  READ filters       CONSTANT)   // [{label, patterns:[glob…]}]
    Q_PROPERTY(QString currentFilter READ currentFilter CONSTANT)   // a label, or ""
public:
    explicit Picker(const QString& requestFile, QObject* parent = nullptr);
    QString mode() const { return m_mode; }
    QString title() const { return m_title; }
    QString acceptLabel() const { return m_accept; }
    bool multiple() const { return m_multiple; }
    bool directory() const { return m_directory; }
    QString currentName() const { return m_currentName; }
    QString currentFolder() const { return m_currentFolder; }
    QVariantList filters() const { return m_filters; }
    QString currentFilter() const { return m_currentFilter; }

    Q_INVOKABLE void answer(const QString& root, const QStringList& paths);
    Q_INVOKABLE void cancel();
private:
    void finish(const QByteArray& json);
    QString m_mode = "open", m_title, m_accept, m_currentName, m_currentFolder, m_currentFilter;
    bool m_multiple = false, m_directory = false, m_done = false;
    QVariantList m_filters;
};
