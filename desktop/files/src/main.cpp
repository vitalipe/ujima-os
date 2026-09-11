#include <QCommandLineParser>
#include <QFont>
#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QUrl>
#include "dirmodel.h"
#include "files.h"
#include "glyphs.h"
#include "places.h"
#include "prefs.h"

int main(int argc, char** argv) {
    // the session points Qt apps at the GTK theme (Marble, Stellarium); this one paints itself
    qunsetenv("QT_QPA_PLATFORMTHEME");
    QGuiApplication::setApplicationName("ujima-files");   // WM_CLASS class, the projection's match
    QGuiApplication::setOrganizationName("ujima");
    QGuiApplication app(argc, argv);
    app.setFont(QFont("Public Sans"));

    QCommandLineParser cli;
    cli.addHelpOption();
    cli.addOption({"text-size",    "Normal | Large | Extra large", "size", "Normal"});
    cli.addOption({"single-click", "Open on a single click"});
    cli.addOption({"qml",          "QML directory", "dir", QCoreApplication::applicationDirPath() + "/../qml"});
    cli.addOption({"places",       "Places stream URL", "url", "http://127.0.0.1:1336/ujima-desktop/stream/places"});
    cli.process(app);

    Prefs       prefs(cli.value("text-size"), cli.isSet("single-click"));
    PlacesModel places(QUrl(cli.value("places")));
    DirModel    dir;
    Files       files;

    QQmlApplicationEngine engine;
    engine.addImageProvider("glyph", new Glyphs);
    engine.rootContext()->setContextProperty("prefs",   &prefs);
    engine.rootContext()->setContextProperty("places",  &places);
    engine.rootContext()->setContextProperty("listing", &dir);
    engine.rootContext()->setContextProperty("files",   &files);

    const QString qmlDir = cli.value("qml");
    engine.addImportPath(qmlDir);
    engine.load(QUrl::fromLocalFile(qmlDir + "/Main.qml"));
    if (engine.rootObjects().isEmpty()) return 1;
    return app.exec();
}
