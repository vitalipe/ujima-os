#pragma once
#include <QGuiApplication>
#include <QWindow>
#include <QtGui/qguiapplication_platform.h>
#include <xcb/xcb.h>
#include <cstdlib>
#include <cstring>

// An i3 dock: i3 puts the window in the output's dock area (top or bottom by its position, at its
// own height) and the workspaces get the rest — so no gaps, and an i3-fullscreen app covers it.
// DOCK and nothing else: Qt's own typing appends NORMAL, and picom's scrim dims anything normal
// and unfocused. Set on the created, unmapped window; Qt leaves the property alone when it maps.
inline void makeDock(QWindow* w) {
    auto* x11 = qGuiApp->nativeInterface<QNativeInterface::QX11Application>();
    if (!x11) return;
    xcb_connection_t* c = x11->connection();
    auto atom = [c](const char* name) {
        xcb_intern_atom_reply_t* r = xcb_intern_atom_reply(c, xcb_intern_atom(c, 0, strlen(name), name), nullptr);
        const xcb_atom_t a = r ? r->atom : XCB_NONE;
        free(r);
        return a;
    };
    const xcb_atom_t type = atom("_NET_WM_WINDOW_TYPE");
    const xcb_atom_t dock = atom("_NET_WM_WINDOW_TYPE_DOCK");
    w->create();
    xcb_change_property(c, XCB_PROP_MODE_REPLACE, w->winId(), type, XCB_ATOM_ATOM, 32, 1, &dock);
    xcb_flush(c);
}
