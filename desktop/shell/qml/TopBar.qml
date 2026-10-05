import QtQuick
import QtQuick.Window
import "."

// The top pane (v0.9 spec 33a): the focused app's icon · its title · close. Bare at home, where
// the wallpaper on the X root shows through. An i3 dock: the host types it before it maps.
Window {
    id: bar
    required property var tip
    readonly property var app: desktop.current
    readonly property bool home: !app.id

    title: "ujima-topbar"
    flags: Qt.FramelessWindowHint | Qt.WindowDoesNotAcceptFocus
    x: 0; y: 0; width: Screen.width; height: Theme.barTop
    color: "transparent"
    visible: desktop.hasApps && !desktop.barsHidden

    Rectangle {
        anchors.fill: parent
        visible: !bar.home
        color: Theme.panel
        Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.panelBrd }
    }

    // the id cell: 48px, the icon centred in it
    Image {
        x: (48 - width) / 2; anchors.verticalCenter: parent.verticalCenter
        width: 20; height: 20; sourceSize: Qt.size(20, 20)
        source: bar.home ? "" : bar.app.icon
        visible: !bar.home
    }

    Text {
        anchors { left: parent.left; leftMargin: 48; right: close.left; verticalCenter: parent.verticalCenter }
        text: bar.home ? "" : (bar.app.title || "")
        color: Theme.text; elide: Text.ElideRight
        font { family: Theme.font; pixelSize: 13 }
    }

    // chipless: the red is baked into the glyph, only the hover fill is drawn
    Rectangle {
        id: close
        anchors { right: parent.right; rightMargin: 12; verticalCenter: parent.verticalCenter }
        width: 34; height: 34; radius: 10
        visible: !bar.home
        color: closeHover.hovered ? Theme.tint([214, 131, 131], .20) : "transparent"
        Behavior on color { ColorAnimation { duration: 150 } }
        Image {
            anchors.centerIn: parent
            width: 17; height: 17; sourceSize: Qt.size(17, 17)
            source: Theme.icon("close.svg")
        }
        Tip { id: closeHover; tip: bar.tip; text: "Close " + (bar.app.label || "") }
        TapHandler { onTapped: desktop.closeApp() }
    }
}
