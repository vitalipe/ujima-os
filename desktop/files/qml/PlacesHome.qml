import QtQuick
import "."

// HOME: "Where are your files?" — one card per ready place from the stream
Flickable {
    id: home
    signal enter(int row)
    contentHeight: body.implicitHeight + 64
    clip: true

    Column {
        id: body
        anchors { left: parent.left; right: parent.right; top: parent.top; leftMargin: 46; rightMargin: 46; topMargin: 34 }
        spacing: 26

        Item {
            width: parent.width; height: head.implicitHeight
            Column {
                id: head
                spacing: 4
                Text { text: "Where are your files?"; color: Theme.text; font.pixelSize: prefs.title; font.weight: Font.Bold; font.letterSpacing: -.5; font.family: Theme.font }
                Text { text: "Choose a place to open it"; color: Theme.dim; font.pixelSize: prefs.sub; font.family: Theme.font }
            }
            Rectangle {
                anchors.right: parent.right; anchors.bottom: parent.bottom
                height: hint.implicitHeight + 18; width: hintRow.implicitWidth + 28; radius: 999
                color: Qt.rgba(1, 1, 1, .05); border.width: 1; border.color: Qt.rgba(1, 1, 1, .08)
                Row {
                    id: hintRow; anchors.centerIn: parent; spacing: 8
                    Glyph { name: "plus"; color: Theme.frost; size: 14; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
                    Text { id: hint; text: "Plug in a USB stick and it shows up here"; color: Theme.dim; font.pixelSize: prefs.en; font.weight: Font.DemiBold; font.family: Theme.font }
                }
            }
        }

        Grid {
            id: grid
            width: parent.width
            columns: 2
            columnSpacing: 18; rowSpacing: 18
            readonly property real cellW: (width - columnSpacing) / 2
            Repeater {
                model: places
                delegate: PlaceCard {
                    required property int index
                    required property var model
                    width: grid.cellW
                    place: model
                    onOpen: home.enter(index)
                }
            }
        }

        Text {
            visible: places.count === 0
            width: parent.width
            text: places.connected ? "No place is ready yet." : "Waiting for ujimad…"
            color: Theme.faint; font.pixelSize: prefs.sub; font.family: Theme.font
        }
    }
}
