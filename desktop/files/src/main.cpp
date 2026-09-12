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
#include "picker.h"
#include "prefs.h"

int main(int argc, char** argv) {
    // the session points Qt apps at the GTK theme (Marble, Stellarium); this one paints itself
    qunsetenv("QT_QPA_PLATFORMTHEME");
    // --pick = a portal dialog: its own WM_CLASS, untracked by the projection, so it lands in
    // the asking app's workspace instead of switching to the Files app's
    bool pick = false;
    for (int i = 1; i < argc; ++i) if (QString::fromLocal8Bit(argv[i]) == "--pick") pick = true;
    QGuiApplication::setApplicationName(pick ? "ujima-filepicker" : "ujima-files");   // WM_CLASS class
    QGuiApplication::setOrganizationName("ujima");
    QGuiApplication app(argc, argv);
    app.setFont(QFont("Public Sans"));

    QCommandLineParser cli;
    cli.addHelpOption();
    cli.addOption({"text-size",    "Normal | Large | Extra large", "size", "Normal"});
    cli.addOption({"single-click", "Open on a single click"});
    cli.addOption({"qml",          "QML directory", "dir", QCoreApplication::applicationDirPath() + "/../qml"});
    cli.addOption({"places",       "Places stream URL", "url", "http://127.0.0.1:1336/ujima-desktop/stream/places"});
    cli.addOption({"pick",         "Run as a portal file dialog: the request JSON file", "file"});
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
    Picker* picker = pick ? new Picker(cli.value("pick"), &app) : nullptr;
    if (picker) engine.rootContext()->setContextProperty("pick", picker);
    engine.load(QUrl::fromLocalFile(qmlDir + (pick ? "/Picker.qml" : "/Main.qml")));
    if (engine.rootObjects().isEmpty()) return 1;
    return app.exec();
}
