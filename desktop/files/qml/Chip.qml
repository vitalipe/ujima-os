import QtQuick
import "."

// a small pill: an icon + a word. dim = a stick's label, frost = a token it carries or "can change",
// plain = "view only", danger = a share that can't be opened
Rectangle {
    property string text: ""
    property string glyph: "tag"
    property string tone: "dim"
    readonly property color fg: tone === "frost" ? Theme.frost : tone === "danger" ? Theme.dangerText : tone === "plain" ? "#c2cad6" : Theme.dim
    height: label.implicitHeight + 8
    width: row.implicitWidth + 20
    radius: 999
    color: tone === "frost" ? Theme.tint(Theme.frostRgb, .12) : tone === "danger" ? Theme.tint(Theme.dangerRgb, .14) : Qt.rgba(1, 1, 1, tone === "plain" ? .06 : .05)
    border.width: 1
    border.color: tone === "frost" ? Theme.tint(Theme.frostRgb, .28) : tone === "danger" ? Theme.tint(Theme.dangerRgb, .28) : Qt.rgba(1, 1, 1, tone === "plain" ? .12 : .07)
    Row {
        id: row
        anchors.centerIn: parent
        spacing: 6
        Glyph { name: glyph; color: fg; size: 13; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
        Text { id: label; text: parent.parent.text; color: fg; font.pixelSize: prefs.en; font.weight: Font.DemiBold; font.family: Theme.font }
    }
}
