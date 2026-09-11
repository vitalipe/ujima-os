import QtQuick
import QtQuick.Shapes
import "."

// one mount point, as the design's home card
Rectangle {
    id: card
    property var place        // row map from the places model
    property var m: Theme.meta(place.kind, place.label)
    signal open()

    readonly property bool nearlyFull: place.pct >= 90
    readonly property color fill: nearlyFull ? Theme.full : m.color
    readonly property bool hovered: hh.hovered

    radius: 18
    color: Qt.rgba(1, 1, 1, hovered ? .06 : .03)
    Behavior on color { ColorAnimation { duration: 160 } }
    border.width: m.dashed ? 0 : 1.5
    border.color: Theme.tint(m.rgb, .30)
    implicitHeight: col.implicitHeight + 46

    // the design's dashed ring for an ephemeral place (a Rectangle border can't dash)
    Shape {
        visible: m.dashed
        anchors.fill: parent
        ShapePath {
            strokeColor: Theme.tint(m.rgb, .42); strokeWidth: 1.5; fillColor: "transparent"
            strokeStyle: ShapePath.DashLine; dashPattern: [4, 3]
            PathRectangle { x: 0.75; y: 0.75; width: card.width - 1.5; height: card.height - 1.5; radius: 18 }
        }
    }

    Column {
        id: col
        anchors { left: parent.left; right: parent.right; top: parent.top; margins: 24; leftMargin: 26; rightMargin: 26 }
        spacing: 20

        Row {
            width: parent.width
            spacing: 18
            Rectangle {
                width: 62; height: 62; radius: 18
                color: Theme.tint(m.rgb, .14)
                border.width: 1; border.color: Theme.tint(m.rgb, .3)
                anchors.verticalCenter: parent.verticalCenter
                Glyph { anchors.centerIn: parent; name: m.glyph; color: m.color; size: 32; stroke: 1.6 }
            }
            Column {
                width: parent.width - 62 - 18 - 22 - 18
                spacing: 4
                anchors.verticalCenter: parent.verticalCenter
                Row {
                    width: parent.width
                    spacing: 10
                    Text { text: m.name + (place.suffix > 1 ? " " + place.suffix : ""); color: Theme.text; font.pixelSize: prefs.card; font.weight: Font.Bold; font.letterSpacing: -.3; font.family: Theme.font }
                    Rectangle {
                        height: badge.implicitHeight + 8; width: badge.implicitWidth + 20; radius: 999
                        color: Theme.tint(m.rgb, .14); border.width: 1; border.color: Theme.tint(m.rgb, .28)
                        anchors.verticalCenter: parent.verticalCenter
                        Text { id: badge; anchors.centerIn: parent; text: m.badge; color: m.color; font.pixelSize: prefs.badge; font.weight: Font.Bold; font.letterSpacing: .2; font.family: Theme.font }
                    }
                }
                Text { width: parent.width; text: m.desc; color: Theme.dim; font.pixelSize: prefs.sub; wrapMode: Text.WordWrap; lineHeight: 1.35; font.family: Theme.font }
                // chips: the stick's label (never for this computer / temporary) + the token types it carries
                Item { width: 1; height: chips.visible ? 7 : 0 }
                Flow {
                    id: chips
                    width: parent.width
                    spacing: 7
                    readonly property bool labelled: !!place.label && place.kind !== "local" && place.kind !== "session"
                    readonly property var tokenList: (place.tokens || []).map(Theme.tokenMeta).filter(function(t) { return t })
                    visible: labelled || tokenList.length > 0
                    Chip { visible: chips.labelled; text: place.label || ""; glyph: "tag"; tone: "dim" }
                    Repeater {
                        model: chips.tokenList
                        delegate: Chip { required property var modelData; text: modelData.text; glyph: modelData.glyph; tone: "frost" }
                    }
                }
            }
            Glyph { name: "chevron"; color: m.color; size: 22; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
        }

        Column {
            width: parent.width
            spacing: 9
            Rectangle {
                width: parent.width; height: 10; radius: 999
                color: Qt.rgba(1, 1, 1, .07)
                Rectangle {
                    height: parent.height; radius: 999
                    width: parent.width * place.pct / 100
                    color: card.fill
                    Behavior on width { NumberAnimation { duration: 300 } }
                }
            }
            Item {
                width: parent.width; height: spaceLabel.implicitHeight
                Text { id: spaceLabel; anchors.left: parent.left; color: nearlyFull ? Theme.dangerText : Theme.text; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font
                       text: nearlyFull ? "Nearly full — " + Theme.fmtBytes(place.free) + " left" : Theme.fmtBytes(place.free) + " free" }
                Text { anchors.right: parent.right; anchors.baseline: spaceLabel.baseline; color: Theme.dim; font.pixelSize: prefs.en; font.family: Theme.font
                       text: Theme.fmtBytes(place.used) + " of " + Theme.fmtBytes(place.total) + " used" }
            }
        }
    }
    HoverHandler { id: hh; cursorShape: Qt.PointingHandCursor }
    TapHandler { onTapped: card.open() }
}
