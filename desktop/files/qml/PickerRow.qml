import QtQuick
import "."

// one listing row: icon · name · size · modified — the web picker's .row
Rectangle {
    id: row
    property string name: ""
    property string type: "file"
    property bool isDir: false
    property real size: 0
    property real mtime: 0
    property bool selected: false
    property bool inert: false            // files while picking a folder
    signal clicked(bool toggle)
    signal doubleClicked()

    height: 44
    radius: 11
    color: selected ? Theme.tint(Theme.frostRgb, .13) : (ma.containsMouse && !inert ? Qt.rgba(1, 1, 1, .04) : "transparent")
    border.width: selected ? 1 : 0
    border.color: Theme.tint(Theme.frostRgb, .35)
    opacity: inert ? .45 : 1

    function fmtSize(n) {
        if (n < 1024) return n + " B"
        if (n < 1048576) return (n / 1024).toFixed(n < 10240 ? 1 : 0) + " KB"
        if (n < 1073741824) return (n / 1048576).toFixed(1) + " MB"
        return (n / 1073741824).toFixed(1) + " GB"
    }
    function fmtDate(ms) {
        if (!ms) return ""
        const d = new Date(ms), now = new Date()
        if (d.toDateString() === now.toDateString()) return Qt.formatTime(d, "hh:mm")
        return Qt.formatDate(d, d.getFullYear() === now.getFullYear() ? "MMM d" : "MMM d, yyyy")
    }

    Row {
        anchors { fill: parent; leftMargin: 20; rightMargin: 16 }
        spacing: 12
        Glyph { name: row.isDir ? "folder" : row.type; color: row.isDir ? Theme.frost : Theme.faint; size: 20; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
        Text { width: parent.width - 20 - 12 - 90 - 12 - 132 - 12; text: row.name; color: Theme.text; font.pixelSize: 14; font.family: Theme.font; elide: Text.ElideRight; anchors.verticalCenter: parent.verticalCenter }
        Text { width: 90; text: row.isDir ? "" : row.fmtSize(row.size); color: Theme.dim; font.pixelSize: 13; font.family: Theme.font; horizontalAlignment: Text.AlignRight; anchors.verticalCenter: parent.verticalCenter }
        Text { width: 132; text: row.fmtDate(row.mtime); color: Theme.dim; font.pixelSize: 13; font.family: Theme.font; horizontalAlignment: Text.AlignRight; anchors.verticalCenter: parent.verticalCenter }
    }
    MouseArea {
        id: ma
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: row.inert ? Qt.ArrowCursor : Qt.PointingHandCursor
        onClicked: (mouse) => row.clicked(!!(mouse.modifiers & (Qt.ControlModifier | Qt.MetaModifier)))
        onDoubleClicked: row.doubleClicked()
    }
}
