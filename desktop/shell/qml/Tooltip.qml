import QtQuick
import QtQuick.Window
import "."

// The one tooltip card, reused by every bar item: its own window, since a card inside the 68px
// dock would clip. Placed as GTK places one: a small item (32px or less) anchors it on itself, a
// bigger one on the pointer where it rested; below when there is room, else above; centred, and
// slid to stay on screen (offsets measured off GTK's). A neighbour while one shows is instant.
Window {
    id: tip
    flags: Qt.ToolTip | Qt.FramelessWindowHint | Qt.WindowDoesNotAcceptFocus
    color: "transparent"
    width: card.width; height: card.height

    property Item target: null
    property string text: ""
    property point at                 // the pointer, global

    function show(item, text, at) {
        target = item; tip.text = text; tip.at = at
        if (visible) place(); else rest.restart()
    }
    function track(item, at) { if (target === item && !visible) tip.at = at }
    function hide(item) {
        if (target !== item) return
        rest.stop(); visible = false; target = null
    }
    function place() {
        if (!target) return
        const small  = target.height <= 32
        const corner = target.mapToGlobal(0, 0)
        const top    = small ? corner.y : at.y
        const bottom = small ? corner.y + target.height : at.y
        const centre = small ? corner.x + target.width / 2 : at.x
        const below  = bottom + (small ? 4 : 20)
        y = below + height <= Screen.height ? below : top - 4 - height
        x = Math.round(Math.max(0, Math.min(Screen.width - width, centre - width / 2)))
        visible = true
    }

    Timer { id: rest; interval: 500; onTriggered: tip.place() }

    Rectangle {
        id: card
        width: label.implicitWidth + 32; height: label.implicitHeight + 22
        radius: 10; color: "#232a33"; border { width: 1; color: "#39424f" }
        Text {
            id: label
            anchors.centerIn: parent
            text: tip.text; color: Theme.text
            font { family: Theme.font; pixelSize: 12; weight: Font.Medium }
        }
    }
}
