#include <QCommandLineParser>
#include <QFont>
#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QUrl>
#include "desktop.h"

int main(int argc, char** argv) {
    // the session points Qt apps at the GTK theme (Marble, Stellarium); this one paints itself
    qunsetenv("QT_QPA_PLATFORMTHEME");
    // a static grid needs no GPU: the software rasterizer is the cheaper floor for the one window
    // that is always alive (numbers in the commit). The env still wins, for a comparison.
    if (!qEnvironmentVariableIsSet("QT_QUICK_BACKEND")) qputenv("QT_QUICK_BACKEND", "software");
    QGuiApplication::setApplicationName("ujima-launcher");   // WM_CLASS class — not an app, ujimad keeps it home
    QGuiApplication::setOrganizationName("ujima");
    QGuiApplication app(argc, argv);
    app.setFont(QFont("Public Sans"));

    QCommandLineParser cli;
    cli.addHelpOption();
    cli.addOption({"qml",     "QML directory", "dir", QCoreApplication::applicationDirPath() + "/../qml"});
    cli.addOption({"desktop", "ujimad's desktop tier", "url", "http://127.0.0.1:1336/ujima-desktop"});
    cli.process(app);

    Desktop desktop(QUrl(cli.value("desktop")));

    QQmlApplicationEngine engine;
    engine.rootContext()->setContextProperty("desktop", &desktop);

    const QString qmlDir = cli.value("qml");
    engine.addImportPath(qmlDir);
    engine.load(QUrl::fromLocalFile(qmlDir + "/Main.qml"));
    if (engine.rootObjects().isEmpty()) return 1;
    return app.exec();
}
