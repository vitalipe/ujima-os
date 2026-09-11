import QtQuick
import "."

// a small pill: an icon + a word. dim = a stick's label, frost = a token it carries
Rectangle {
    property string text: ""
    property string glyph: "tag"
    property string tone: "dim"
    readonly property color fg: tone === "frost" ? Theme.frost : Theme.dim
    height: label.implicitHeight + 8
    width: row.implicitWidth + 20
    radius: 999
    color: tone === "frost" ? Theme.tint(Theme.frostRgb, .12) : Qt.rgba(1, 1, 1, .05)
    border.width: 1
    border.color: tone === "frost" ? Theme.tint(Theme.frostRgb, .28) : Qt.rgba(1, 1, 1, .07)
    Row {
        id: row
        anchors.centerIn: parent
        spacing: 6
        Glyph { name: glyph; color: fg; size: 13; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
        Text { id: label; text: parent.parent.text; color: fg; font.pixelSize: prefs.en; font.weight: Font.DemiBold; font.family: Theme.font }
    }
}
