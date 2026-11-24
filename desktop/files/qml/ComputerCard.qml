import QtQuick
import "."

// one nearby computer: its name, then a row per folder it shares
Rectangle {
    id: card
    property var host                 // {id, name, rw, shares} from places.nearby
    property string opening: ""       // the place id of the share being opened
    signal open(var share)

    readonly property int n: host.shares.length
    radius: 18
    color: Qt.rgba(1, 1, 1, .03)
    border.width: 1.5
    border.color: Theme.tint(Theme.peerRgb, .3)
    implicitHeight: col.implicitHeight + 32

    Column {
        id: col
        anchors { left: parent.left; right: parent.right; top: parent.top; topMargin: 20; leftMargin: 22; rightMargin: 22 }

        Item {
            width: parent.width; height: 60
            Rectangle {
                id: icon
                width: 48; height: 48; radius: 14
                color: Theme.tint(Theme.peerRgb, .14)
                border.width: 1; border.color: Theme.tint(Theme.peerRgb, .3)
                Glyph { anchors.centerIn: parent; name: "computer"; color: Theme.peer; size: 26; stroke: 1.6 }
            }
            Column {
                anchors { left: icon.right; leftMargin: 14; right: parent.right; verticalCenter: icon.verticalCenter }
                spacing: 3
                Text { textFormat: Text.PlainText; width: parent.width; text: card.host.name; color: Theme.text; elide: Text.ElideRight
                       font.pixelSize: prefs.card; font.weight: Font.Bold; font.letterSpacing: -.3; font.family: Theme.font }
                Text { textFormat: Text.PlainText; width: parent.width; color: Theme.dim; elide: Text.ElideRight; font.pixelSize: prefs.sub; font.family: Theme.font
                       text: card.n + (card.n === 1 ? " shared folder" : " shared folders")
                             + (card.host.rw ? " · " + card.host.rw + " you can change" : " · view only") }
            }
        }

        Repeater {
            model: card.host.shares
            delegate: Item {
                id: row
                required property var modelData
                readonly property bool opening: card.opening === modelData.id
                readonly property bool failed: modelData.state === "invalid" && !opening
                readonly property var access: Theme.accessChip(modelData.access)
                width: col.width
                height: line.height + (failed ? why.implicitHeight + 9 : 0)

                Rectangle {
                    x: -10; width: parent.width + 20; height: parent.height; radius: 12
                    color: Qt.rgba(1, 1, 1, rh.hovered ? .06 : 0)
                    Behavior on color { ColorAnimation { duration: 160 } }
                }
                Rectangle { width: parent.width; height: 1; color: Qt.rgba(1, 1, 1, .05) }

                Item {
                    id: line
                    width: parent.width; height: 50
                    Glyph { id: folder; name: "folder"; color: Theme.peer; size: 24; stroke: 1.7
                            anchors { left: parent.left; leftMargin: 2; verticalCenter: parent.verticalCenter } }
                    Glyph { id: arrow; name: "chevron"; color: Theme.peer; size: 20; stroke: 2; opacity: row.opening ? 0 : 1
                            anchors { right: parent.right; rightMargin: 2; verticalCenter: parent.verticalCenter } }
                    Chip {
                        id: chip
                        anchors { right: arrow.left; rightMargin: 13; verticalCenter: parent.verticalCenter }
                        text:  row.opening ? "Opening…" : row.failed ? "Can't open" : row.access.text
                        glyph: row.opening ? "open"     : row.failed ? "warn"       : row.access.glyph
                        tone:  row.opening ? "frost"    : row.failed ? "danger"     : row.access.tone
                    }
                    Text { textFormat: Text.PlainText; anchors { left: folder.right; leftMargin: 13; right: chip.left; rightMargin: 13; verticalCenter: parent.verticalCenter }
                           text: row.modelData.label; color: Theme.text; elide: Text.ElideRight
                           font.pixelSize: prefs.name + 1.5; font.weight: Font.DemiBold; font.family: Theme.font }
                }
                Text {
                    id: why
                    textFormat: Text.PlainText
                    visible: row.failed
                    anchors { left: parent.left; leftMargin: 39; right: parent.right; rightMargin: 2; top: line.bottom; topMargin: -5 }
                    wrapMode: Text.Wrap; maximumLineCount: 3; elide: Text.ElideRight; lineHeight: 1.3
                    color: Theme.dim; font.pixelSize: prefs.en; font.family: Theme.font
                    text: "Tap to try again. Details for staff: " + row.modelData.reason.replace(/\s+/g, " ").trim()
                }
                HoverHandler { id: rh; cursorShape: Qt.PointingHandCursor }
                TapHandler { onTapped: card.open(row.modelData) }
            }
        }
    }
}
