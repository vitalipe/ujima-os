import QtQuick
import QtQuick.Window
import "."

// The one tooltip card, reused by every bar item: its own window, since a card inside the 68px
// dock would clip. Shows after a short rest; moving to a neighbour while one shows is instant.
Window {
    id: tip
    flags: Qt.ToolTip | Qt.FramelessWindowHint | Qt.WindowDoesNotAcceptFocus
    color: "transparent"
    width: card.width; height: card.height

    property Item target: null
    property string text: ""
    property bool above: true     // the dock's open upwards, the top bar's downwards

    function show(item, text, above) {
        target = item; tip.text = text; tip.above = above
        if (visible) place(); else rest.restart()
    }
    function hide(item) {
        if (target !== item) return
        rest.stop(); visible = false; target = null
    }
    function place() {
        if (!target) return
        const p = target.mapToGlobal(target.width / 2, above ? 0 : target.height)
        x = Math.round(Math.max(4, Math.min(Screen.width - width - 4, p.x - width / 2)))
        y = Math.round(above ? p.y - height - 6 : p.y + 6)
        visible = true
    }

    Timer { id: rest; interval: 500; onTriggered: tip.place() }

    Rectangle {
        id: card
        width: label.implicitWidth + 20; height: label.implicitHeight + 10
        radius: 10; color: "#232a33"; border { width: 1; color: "#39424f" }
        Text {
            id: label
            anchors.centerIn: parent
            text: tip.text; color: Theme.text
            font { family: Theme.font; pixelSize: 12; weight: Font.Medium }
        }
    }
}
