import QtQuick
import "."

// one tab of the home strip: glyph, label, how many
Rectangle {
    id: tab
    property string label: ""
    property string glyph: ""
    property color tint: Theme.text
    property var rgb: Theme.whiteRgb
    property int count: 0
    property bool on: false
    signal clicked()

    height: 52
    width: row.implicitWidth + 31
    radius: 12
    color: on ? Theme.tint(rgb, .2) : "transparent"
    border.width: on ? 1.5 : 0
    border.color: Theme.tint(rgb, .5)
    Behavior on color { ColorAnimation { duration: 160 } }

    Row {
        id: row
        anchors.centerIn: parent
        spacing: 10
        Glyph { name: tab.glyph; color: tab.on ? tab.tint : Theme.dim; size: 20; stroke: 1.8; anchors.verticalCenter: parent.verticalCenter }
        Text { textFormat: Text.PlainText; text: tab.label; color: tab.on ? Theme.text : Theme.dim; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font; anchors.verticalCenter: parent.verticalCenter }
        Rectangle {
            height: 24; width: Math.max(24, n.implicitWidth + 14); radius: 999
            color: tab.on ? Theme.tint(tab.rgb, .32) : Qt.rgba(1, 1, 1, .07)
            anchors.verticalCenter: parent.verticalCenter
            Text { id: n; textFormat: Text.PlainText; anchors.centerIn: parent; text: tab.count; color: tab.on ? Theme.text : Theme.dim; font.pixelSize: 12; font.weight: Font.Bold; font.family: Theme.font }
        }
    }
    HoverHandler { cursorShape: Qt.PointingHandCursor }
    TapHandler { onTapped: tab.clicked() }
}
