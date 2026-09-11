import QtQuick
import "."

Rectangle {
    id: toast
    property string text: ""
    readonly property bool shown: text !== ""
    anchors { horizontalCenter: parent.horizontalCenter; bottom: parent.bottom; bottomMargin: 30 }
    height: 50; width: row.implicitWidth + 40; radius: 14
    color: Theme.panel
    border.width: 1; border.color: Theme.tint(Theme.frostRgb, .35)
    opacity: shown ? 1 : 0
    transform: Translate { y: toast.shown ? 0 : 12; Behavior on y { NumberAnimation { duration: 200 } } }
    Behavior on opacity { NumberAnimation { duration: 200 } }
    Row {
        id: row; anchors.centerIn: parent; spacing: 10
        Glyph { name: "open"; color: Theme.frost; size: 20; stroke: 1.9; anchors.verticalCenter: parent.verticalCenter }
        Text { text: toast.text; color: Theme.text; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font }
    }
}
