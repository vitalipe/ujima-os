#include "splash.h"
#include <QFile>
#include <QGuiApplication>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QScreen>
#include <QUrl>
#include <QtGui/qguiapplication_platform.h>
#include <cstdlib>
#include <cstring>

Splash::Splash(const QString& framesDir, QObject* parent) : QObject(parent) {
    auto* x11 = qGuiApp->nativeInterface<QNativeInterface::QX11Application>();
    if (!x11) return;
    m_c = x11->connection();
    m_root = xcb_setup_roots_iterator(xcb_get_setup(m_c)).data->root;

    QFile f(framesDir + "/frames.json");
    if (!f.open(QIODevice::ReadOnly)) return;
    const QJsonObject m = QJsonDocument::fromJson(f.readAll()).object();
    m_fps = m.value("fps").toInt(25);
    m_loop = m.value("loop").toInt();
    m_outro = m.value("outro").toInt();

    // the set nearest the screen height by ratio — the boot splash picks the same one, so the mark
    // keeps its size across the hand-over
    const QScreen* screen = QGuiApplication::primaryScreen();
    const double h = screen->size().height() * screen->devicePixelRatio();
    auto ratio = [h](double x) { return x > h ? x / h : h / x; };
    int best = 0;
    for (const QJsonValue v : m.value("heights").toArray())
        if (!best || ratio(v.toInt()) < ratio(best)) best = v.toInt();
    if (!best || m_loop <= 0 || m_outro <= 0) return;
    m_dir = QUrl::fromLocalFile(framesDir + "/" + QString::number(best)).toString();

    xcb_get_property_reply_t* r = xcb_get_property_reply(
        m_c, xcb_get_property(m_c, 0, m_root, marker(), XCB_ATOM_CARDINAL, 0, 1), nullptr);
    m_cover = !(r && r->type != XCB_NONE);
    free(r);
}

xcb_atom_t Splash::marker() const {
    const char* name = "_UJIMA_BOOT_COVER";
    xcb_intern_atom_reply_t* r = xcb_intern_atom_reply(m_c, xcb_intern_atom(m_c, 0, strlen(name), name), nullptr);
    const xcb_atom_t a = r ? r->atom : XCB_NONE;
    free(r);
    return a;
}

// i3 maps its frames (the launcher's, the bars') above everything, so every map or restack under
// the root while the cover is up puts it back on top
void Splash::keepOnTop(QWindow* window) {
    if (!m_c) return;
    m_window = window->winId();
    xcb_get_window_attributes_reply_t* a =
        xcb_get_window_attributes_reply(m_c, xcb_get_window_attributes(m_c, m_root), nullptr);
    m_rootMask = a ? a->your_event_mask : 0;
    m_ownRootEvents = !(m_rootMask & XCB_EVENT_MASK_SUBSTRUCTURE_NOTIFY);
    free(a);
    const uint32_t mask = m_rootMask | XCB_EVENT_MASK_SUBSTRUCTURE_NOTIFY;
    xcb_change_window_attributes(m_c, m_root, XCB_CW_EVENT_MASK, &mask);
    qGuiApp->installNativeEventFilter(this);
    raise();
}

void Splash::raise() const {
    const uint32_t above = XCB_STACK_MODE_ABOVE;
    xcb_configure_window(m_c, m_window, XCB_CONFIG_WINDOW_STACK_MODE, &above);
    xcb_flush(m_c);
}

// the root's copies of other windows' map/configure events exist only because we asked: swallowed, since
// Qt reads a configure reported on the root as a screen change and re-scans the outputs
bool Splash::nativeEventFilter(const QByteArray& type, void* message, qintptr*) {
    if (m_window == XCB_NONE || type != "xcb_generic_event_t") return false;
    auto* e = static_cast<xcb_generic_event_t*>(message);
    switch (e->response_type & ~0x80) {
    case XCB_MAP_NOTIFY: {
        auto* m = reinterpret_cast<xcb_map_notify_event_t*>(e);
        if (m->event != m_root || m->window == m_root) return false;
        if (m->window != m_window) raise();
        return m_ownRootEvents;
    }
    case XCB_CONFIGURE_NOTIFY: {
        auto* c = reinterpret_cast<xcb_configure_notify_event_t*>(e);
        if (c->event != m_root || c->window == m_root) return false;
        if (c->window != m_window) raise();
        return m_ownRootEvents;
    }
    }
    return false;
}

void Splash::done() {
    if (!m_c) return;
    const uint32_t one = 1;
    xcb_change_property(m_c, XCB_PROP_MODE_REPLACE, m_root, marker(), XCB_ATOM_CARDINAL, 32, 1, &one);
    if (m_window != XCB_NONE) {
        qGuiApp->removeNativeEventFilter(this);
        xcb_change_window_attributes(m_c, m_root, XCB_CW_EVENT_MASK, &m_rootMask);
        m_window = XCB_NONE;
    }
    xcb_flush(m_c);
}
