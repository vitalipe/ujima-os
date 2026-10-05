import QtQuick
import "."

// one app: its real icon on a tile carrying a faint hint of its category (49b), blooming to a
// full fill + ring while the app is OPEN (driven live by stream/apps). A click is the open verb.
Rectangle {
    id: tile
    property var app        // {id, label, category, icon}
    property var rgb
    readonly property bool open: desktop.open.indexOf(app.id) >= 0
    readonly property bool hovered: hh.hovered
    readonly property bool loading: icon.status === Image.Loading

    width: 68; height: 68; radius: 18
    color: Theme.tint(rgb, open ? .24 : hovered ? .17 : .10)
    border.width: open ? 1.5 : 1
    border.color: Theme.tint(rgb, open ? .5 : hovered ? .32 : .2)
    Behavior on color { ColorAnimation { duration: 160 } }
    Behavior on border.color { ColorAnimation { duration: 160 } }
    scale: th.pressed ? .93 : 1          // the click effect
    Behavior on scale { NumberAnimation { duration: 100 } }

    Image {
        id: icon
        anchors.centerIn: parent
        width: 39; height: 39
        sourceSize: Qt.size(39, 39)
        fillMode: Image.PreserveAspectFit
        asynchronous: true
        source: app.icon
    }
    // the reveal waits for every icon to be paintable
    Component.onCompleted: if (loading) home.pending++
    onLoadingChanged: home.pending += loading ? 1 : -1

    HoverHandler { id: hh; cursorShape: Qt.PointingHandCursor }
    TapHandler { id: th; onTapped: desktop.openApp(app.id) }
}
