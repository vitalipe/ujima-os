import QtQuick
import "."

// one category column: swatch + label, then its tiles wrapping four to a row
Column {
    id: col
    property var group     // {key, label, rgb, apps}
    readonly property color tone: Theme.tint(group.rgb, 1)
    spacing: 15

    Row {
        spacing: 8
        Rectangle { width: 9; height: 14; radius: 3; color: col.tone; anchors.verticalCenter: parent.verticalCenter }
        Text { text: group.label; color: col.tone; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.5; font.family: Theme.font }
    }

    Flow {
        readonly property int n: group.apps.length
        width: Math.min(302, n * 68 + (n - 1) * 10)   // the mock's max-content column: four tiles
        spacing: 10
        Repeater { model: group.apps; delegate: Tile { required property var modelData; app: modelData; rgb: group.rgb } }
    }
}
