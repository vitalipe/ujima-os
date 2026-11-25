import QtQuick
import "."

// one folder a nearby computer shares, on that computer's screen
Rectangle {
    id: tile
    property var share
    property bool selected: false
    property bool opening: false
    readonly property bool failed: share.state === "invalid" && !opening
    readonly property var access: Theme.accessChip(share.access)
    signal select()
    signal open()

    radius: 16
    color: selected || opening ? Theme.tint(Theme.frostRgb, .12) : Qt.rgba(1, 1, 1, hh.hovered ? .06 : .035)
    border.width: selected || opening ? 2 : 1
    border.color: opening ? Theme.tint(Theme.frostRgb, .5) : selected ? Theme.tint(Theme.frostRgb, .85) : Qt.rgba(1, 1, 1, hh.hovered ? .15 : .07)
    Behavior on color { ColorAnimation { duration: 160 } }

    Column {
        anchors { left: parent.left; right: parent.right; top: parent.top; topMargin: 18; leftMargin: 12; rightMargin: 12 }
        spacing: 9
        Rectangle {
            width: 66; height: 66; radius: 18
            anchors.horizontalCenter: parent.horizontalCenter
            color: Theme.tint(Theme.peerRgb, .12)
            opacity: tile.failed ? .55 : 1
            Glyph { anchors.centerIn: parent; name: "folder"; color: Theme.peer; size: 42; stroke: 1.6 }
        }
        Text {
            textFormat: Text.PlainText
            width: parent.width
            text: tile.share.label; color: tile.failed ? Theme.dim : Theme.text
            font.pixelSize: prefs.name; font.weight: Font.DemiBold; font.family: Theme.font
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.Wrap; maximumLineCount: 2; elide: Text.ElideRight
        }
    }
    Chip {
        anchors { horizontalCenter: parent.horizontalCenter; bottom: parent.bottom; bottomMargin: 14 }
        text:  tile.opening ? "Opening…" : tile.failed ? "Can't open" : tile.access.text
        glyph: tile.opening ? "open"     : tile.failed ? "warn"       : tile.access.glyph
        tone:  tile.opening ? "frost"    : tile.failed ? "danger"     : tile.access.tone
    }
    HoverHandler { id: hh; cursorShape: Qt.PointingHandCursor }
    TapHandler {
        onSingleTapped: prefs.singleClickOpen ? tile.open() : tile.select()
        onDoubleTapped: if (!prefs.singleClickOpen) tile.open()
    }
}
