import QtQuick
import "."

// one entry in a folder
Rectangle {
    id: tile
    property string name: ""
    property string type: "file"
    property string meta: ""
    property bool selected: false
    signal select()
    signal open()

    radius: 16
    color: selected ? Theme.tint(Theme.frostRgb, .12) : Qt.rgba(1, 1, 1, .035)
    border.width: selected ? 2 : 1
    border.color: selected ? Theme.tint(Theme.frostRgb, .85) : Qt.rgba(1, 1, 1, .07)
    Behavior on color { ColorAnimation { duration: 160 } }

    Column {
        anchors { left: parent.left; right: parent.right; top: parent.top; topMargin: 18; leftMargin: 12; rightMargin: 12 }
        spacing: 9
        Rectangle {
            width: 66; height: 66; radius: 18
            anchors.horizontalCenter: parent.horizontalCenter
            color: tile.type === "folder" ? Theme.tint([138, 176, 124], .12) : Qt.rgba(1, 1, 1, .05)
            Glyph { anchors.centerIn: parent; name: tile.type; color: Theme.colorOf(tile.type); size: 42; stroke: 1.6 }
        }
        Text {
            width: parent.width
            text: tile.name; color: Theme.text
            font.pixelSize: prefs.name; font.weight: Font.DemiBold; font.family: Theme.font
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.Wrap; maximumLineCount: 2; elide: Text.ElideRight
        }
        Text { width: parent.width; text: tile.meta; color: Theme.dim; font.pixelSize: prefs.en; horizontalAlignment: Text.AlignHCenter; font.family: Theme.font }
    }
    HoverHandler { cursorShape: Qt.PointingHandCursor }
    TapHandler {
        onSingleTapped: prefs.singleClickOpen ? tile.open() : tile.select()
        onDoubleTapped: if (!prefs.singleClickOpen) tile.open()
    }
}
