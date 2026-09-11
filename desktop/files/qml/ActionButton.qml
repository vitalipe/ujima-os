import QtQuick
import "."

// toolbar button: primary | plain | danger, or disabled
Rectangle {
    id: btn
    property string label: ""
    property string glyph: ""
    property string tone: "plain"
    property real weight: tone === "primary" ? Font.Bold : Font.DemiBold
    signal clicked()

    readonly property bool hovered: hh.hovered && enabled
    height: 48
    width: row.implicitWidth + 34
    radius: 11
    color: !enabled ? Qt.rgba(1, 1, 1, .03)
         : tone === "primary" ? Qt.rgba(136 / 255, 192 / 255, 208 / 255, .92)
         : tone === "danger"  ? Theme.tint(Theme.dangerRgb, .16)
         : Qt.rgba(1, 1, 1, hovered ? .11 : .06)
    border.width: 1
    border.color: !enabled ? Qt.rgba(1, 1, 1, .04)
                : tone === "primary" ? "transparent"
                : tone === "danger" ? Theme.tint(Theme.dangerRgb, .32)
                : Qt.rgba(1, 1, 1, .07)
    Behavior on color { ColorAnimation { duration: 160 } }

    readonly property color fg: !enabled ? "#5d6573"
                              : tone === "primary" ? Theme.ink
                              : tone === "danger" ? Theme.dangerText : Theme.text
    Row {
        id: row
        anchors.centerIn: parent
        spacing: 9
        Glyph { name: btn.glyph; color: btn.fg; size: 19; stroke: btn.tone === "primary" ? 1.9 : 1.8; anchors.verticalCenter: parent.verticalCenter }
        Text { text: btn.label; color: btn.fg; font.pixelSize: prefs.btn; font.weight: btn.weight; font.family: Theme.font; anchors.verticalCenter: parent.verticalCenter }
    }
    HoverHandler { id: hh; cursorShape: btn.enabled ? Qt.PointingHandCursor : Qt.ArrowCursor }
    TapHandler { enabled: btn.enabled; onTapped: btn.clicked() }
}
