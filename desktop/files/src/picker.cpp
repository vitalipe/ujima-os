#include "picker.h"
#include "files.h"
#include <QCoreApplication>
#include <QFile>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <cstdio>

Picker::Picker(const QString& requestFile, QObject* parent) : QObject(parent) {
    QFile f(requestFile);
    if (f.open(QIODevice::ReadOnly)) {
        const QJsonObject o = QJsonDocument::fromJson(f.readAll()).object();
        m_mode          = o.value("mode").toString("open");
        m_title         = o.value("title").toString();
        m_accept        = o.value("acceptLabel").toString().remove('_');   // the mnemonic marker
        m_multiple      = o.value("multiple").toBool();
        m_directory     = o.value("directory").toBool();
        m_currentName   = o.value("currentName").toString();
        m_currentFolder = o.value("currentFolder").toString();
        for (const QJsonValue& v : o.value("filters").toArray()) {
            const QJsonObject fo = v.toObject();
            QStringList patterns;
            for (const QJsonValue& p : fo.value("patterns").toArray()) patterns << p.toString();
            m_filters << QVariantMap{{"label", fo.value("label").toString()}, {"patterns", patterns}};
        }
        const QJsonValue cf = o.value("currentFilter");
        m_currentFilter = cf.isObject() ? cf.toObject().value("label").toString() : cf.toString();
    }
}

void Picker::answer(const QString& root, const QStringList& paths) {
    QJsonArray out;
    for (const QString& p : paths)
        if (Files::insideRoot(root, m_mode == "save" || m_directory ? QFileInfo(p).path() : p)) out << p;
    if (out.isEmpty() && !paths.isEmpty()) { cancel(); return; }   // nothing survived the root check
    finish(QJsonDocument(QJsonObject{{"status", "picked"}, {"paths", out}}).toJson(QJsonDocument::Compact));
}

void Picker::cancel() {
    finish(QJsonDocument(QJsonObject{{"status", "cancelled"}}).toJson(QJsonDocument::Compact));
}

void Picker::finish(const QByteArray& json) {
    if (m_done) return;
    m_done = true;
    std::fwrite(json.constData(), 1, json.size(), stdout);
    std::fputc('\n', stdout);
    std::fflush(stdout);
    QCoreApplication::exit(0);
}
