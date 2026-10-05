#include <QCommandLineParser>
#include <QFont>
#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QTimer>
#include <QUrl>
#include "desktop.h"
#include "nam.h"
#include "notify.h"

int main(int argc, char** argv) {
    // the session points Qt apps at the GTK theme (Marble, Stellarium); this one paints itself
    qunsetenv("QT_QPA_PLATFORMTHEME");
    // the shell is always alive and draws flat surfaces: the software rasterizer is the cheaper
    // floor (numbers in the launcher's commit). The env still wins, for a comparison.
    if (!qEnvironmentVariableIsSet("QT_QUICK_BACKEND")) qputenv("QT_QUICK_BACKEND", "software");
    // WM_CLASS of every shell window: i3 tells the managed ones apart by title (Launcher, Lock)
    QGuiApplication::setApplicationName("ujima-shell");
    QGuiApplication::setOrganizationName("ujima");
    QGuiApplication app(argc, argv);
    app.setFont(QFont("Public Sans"));

    QCommandLineParser cli;
    cli.addHelpOption();
    cli.addOption({"qml",     "QML directory", "dir", QCoreApplication::applicationDirPath() + "/../qml"});
    cli.addOption({"desktop", "ujimad's desktop tier", "url", "http://127.0.0.1:1336/ujima-desktop"});
    cli.process(app);

    Desktop desktop(QUrl(cli.value("desktop")));

    PlainHttpFactory plainHttp;
    QQmlApplicationEngine engine;
    engine.setNetworkAccessManagerFactory(&plainHttp);   // before anything loads: the icons ride it
    engine.rootContext()->setContextProperty("desktop", &desktop);

    const QString qmlDir = cli.value("qml");
    engine.addImportPath(qmlDir);
    engine.load(QUrl::fromLocalFile(qmlDir + "/Main.qml"));
    if (engine.rootObjects().isEmpty()) return 1;

    sdNotify("READY=1");
    QTimer watchdog;
    QObject::connect(&watchdog, &QTimer::timeout, [] { sdNotify("WATCHDOG=1"); });
    watchdog.start(2000);
    return app.exec();
}
