#include <QCommandLineParser>
#include <QDir>
#include <QFont>
#include <QGuiApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QQuickWindow>
#include <QSocketNotifier>
#include <QTimer>
#include <QUrl>
#include <csignal>
#include <sys/socket.h>
#include <unistd.h>
#include "desktop.h"
#include "dock.h"
#include "nam.h"
#include "notify.h"
#include "splash.h"

static int quitPipe[2];

int main(int argc, char** argv) {
    // the session points Qt apps at the GTK theme (Marble, Stellarium); this one paints itself
    qunsetenv("QT_QPA_PLATFORMTHEME");
    // the shell is always alive and draws flat surfaces: the software rasterizer is the cheaper
    // floor (numbers in the launcher's commit). The env still wins, for a comparison.
    if (!qEnvironmentVariableIsSet("QT_QUICK_BACKEND")) qputenv("QT_QUICK_BACKEND", "software");
    // the top bar at home, the lock and the tooltip card are see-through
    QQuickWindow::setDefaultAlphaBuffer(true);
    // WM_CLASS of every shell window: i3 tells the managed ones apart by title (Launcher, Lock)
    QGuiApplication::setApplicationName("ujima-shell");
    QGuiApplication::setOrganizationName("ujima");
    QGuiApplication app(argc, argv);
    app.setFont(QFont("Public Sans"));

    QCommandLineParser cli;
    cli.addHelpOption();
    cli.addOption({"qml",     "QML directory", "dir", QCoreApplication::applicationDirPath() + "/../qml"});
    cli.addOption({"desktop", "ujimad's desktop tier", "url", "http://127.0.0.1:1336/ujima-desktop"});
    cli.addOption({"splash",  "the boot splash's frames", "dir", QCoreApplication::applicationDirPath() + "/../../splash/frames"});
    cli.process(app);

    Desktop desktop(QUrl(cli.value("desktop")));
    Splash splash(QDir::cleanPath(cli.value("splash")));

    PlainHttpFactory plainHttp;
    QQmlApplicationEngine engine;
    engine.setNetworkAccessManagerFactory(&plainHttp);   // before anything loads: the icons ride it
    engine.rootContext()->setContextProperty("desktop", &desktop);
    engine.rootContext()->setContextProperty("splash", &splash);

    const QString qmlDir = cli.value("qml");
    engine.addImportPath(qmlDir);
    engine.load(QUrl::fromLocalFile(qmlDir + "/Main.qml"));
    if (engine.rootObjects().isEmpty()) return 1;

    // the bars are docks before they ever map; they show on the first apps snapshot
    QObject* shell = engine.rootObjects().first();
    for (const char* bar : {"topbar", "dock"})
        makeDock(qobject_cast<QWindow*>(shell->property(bar).value<QObject*>()));

    // SIGTERM (the unit stopping) quits through the event loop, not mid-frame — a clean exit is
    // also what lets a sanitizer build report its leaks
    socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, quitPipe);
    std::signal(SIGTERM, [](int) { const char c = 1; (void) !::write(quitPipe[0], &c, 1); });
    QSocketNotifier quit(quitPipe[1], QSocketNotifier::Read);
    QObject::connect(&quit, &QSocketNotifier::activated, &app, &QCoreApplication::quit);

    // ready once the boot cover is on screen: the wallpaper and the compositor start only then
    // (ujima-session-up), so neither paints over the splash's still mark X is keeping
    if (auto* cover = qobject_cast<QQuickWindow*>(shell->property("cover").value<QObject*>())) {
        splash.keepOnTop(cover);
        QObject::connect(cover, &QQuickWindow::frameSwapped, &app, [] { sdNotify("READY=1"); },
                         Qt::SingleShotConnection);
    } else {
        sdNotify("READY=1");
    }
    QTimer watchdog;
    QObject::connect(&watchdog, &QTimer::timeout, [] { sdNotify("WATCHDOG=1"); });
    watchdog.start(2000);
    return app.exec();
}
