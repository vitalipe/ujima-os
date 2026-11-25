import QtQuick
import "."

// a nearby folder being opened holds the window, so it is entered from where it was asked for.
// Most open at once: the box only shows when the wait is long enough to see
Item {
    id: box
    property bool on: false
    property var share: ({})          // the place being opened
    property bool shown: false
    signal cancel()

    anchors.fill: parent
    visible: on
    onOnChanged: { shown = false; if (on) late.restart(); else late.stop() }
    Timer { id: late; interval: 500; onTriggered: box.shown = true }
    TapHandler { gesturePolicy: TapHandler.ReleaseWithinBounds }   // takes the grab: nothing under it is tapped
    HoverHandler { blocking: true }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(8 / 255, 10 / 255, 13 / 255, .72)
        opacity: box.shown ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: 160 } }
    }

    Rectangle {
        anchors.centerIn: parent
        visible: box.shown
        width: Math.min(520, parent.width - 48)
        height: head.implicitHeight + 48 + foot.height
        radius: 18
        color: Theme.panel
        border.width: 1; border.color: Qt.rgba(1, 1, 1, .06)

        Row {
            id: head
            anchors { left: parent.left; right: parent.right; top: parent.top; margins: 30; topMargin: 26 }
            spacing: 18
            Rectangle {
                width: 52; height: 52; radius: 15
                color: Theme.tint(Theme.peerRgb, .14)
                Glyph {
                    anchors.centerIn: parent; name: "folder"; color: Theme.peer; size: 28; stroke: 1.7
                    SequentialAnimation on opacity {
                        running: box.shown; loops: Animation.Infinite
                        NumberAnimation { from: 1; to: .35; duration: 700; easing.type: Easing.InOutSine }
                        NumberAnimation { from: .35; to: 1; duration: 700; easing.type: Easing.InOutSine }
                    }
                }
            }
            Column {
                width: parent.width - 52 - 18
                spacing: 12
                Text { textFormat: Text.PlainText; width: parent.width; elide: Text.ElideRight; text: "Opening “" + (box.share.label || "") + "”"
                       color: Theme.text; font.pixelSize: prefs.title; font.weight: Font.Bold; font.letterSpacing: -.2; font.family: Theme.font }
                Text { textFormat: Text.PlainText; width: parent.width; wrapMode: Text.WordWrap; lineHeight: 1.45
                       text: "Waiting for " + (box.share.host || "") + " to answer. This can take a moment."
                       color: Theme.dim; font.pixelSize: prefs.btn; font.family: Theme.font }
            }
        }

        Rectangle {
            id: foot
            anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
            height: 86; radius: 18; color: Theme.panel2
            Rectangle { width: parent.width; height: 18; color: parent.color; anchors.top: parent.top }   // square the top corners
            Rectangle { width: parent.width; height: 1; color: Theme.hair; anchors.top: parent.top }
            ActionButton { anchors { right: parent.right; rightMargin: 30; verticalCenter: parent.verticalCenter }
                           label: "Cancel"; onClicked: box.cancel() }
        }
    }
}
