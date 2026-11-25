import QtQuick
import "."

// A NEARBY COMPUTER, opened like a folder: toolbar · notice strip · a tile per folder it shares.
// Nothing is made or changed here; a tile opens that folder
Item {
    id: view
    property var nav
    readonly property var host: nav.host ? nav.host : ({ id: "", name: "", rw: 0, shares: [] })
    readonly property var picked: host.shares.find((s) => s.id === nav.selShare) || null
    readonly property var failed: host.shares.filter((s) => s.state === "invalid" && s.id !== nav.opening)
    readonly property bool retrying: host.id !== "" && nav.retrying === host.id
    readonly property bool trouble: retrying || failed.length > 0

    Column {
        anchors.fill: parent

        Rectangle {
            width: parent.width; height: 80; color: "transparent"
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.hair }
            Row {
                anchors { left: parent.left; verticalCenter: parent.verticalCenter; leftMargin: 26 }
                spacing: 12
                ActionButton { id: back; label: "Back"; glyph: "back"; tone: "plain"; weight: Font.DemiBold; onClicked: nav.goBack() }
                Rectangle {
                    height: 40; width: chip.implicitWidth + 30; radius: 11
                    anchors.verticalCenter: parent.verticalCenter
                    color: Qt.rgba(1, 1, 1, .09)
                    Row {
                        id: chip; anchors.centerIn: parent; spacing: 9
                        Glyph { name: "computer"; color: Theme.peer; size: 18; stroke: 1.8; anchors.verticalCenter: parent.verticalCenter }
                        Text { textFormat: Text.PlainText; text: view.host.name; color: Theme.text; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font; elide: Text.ElideRight
                               width: Math.min(implicitWidth, Math.max(0, view.width - open.width - back.width - 150)) }
                    }
                }
            }
            ActionButton { id: open; anchors { right: parent.right; verticalCenter: parent.verticalCenter; rightMargin: 26 }
                           label: "Open"; glyph: "open"; tone: "primary"; enabled: !!view.picked; onClicked: nav.openShare(view.picked) }
        }

        Rectangle {
            width: parent.width; height: 46
            color: Theme.tint(Theme.peerRgb, .09)
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.tint(Theme.peerRgb, .2) }
            Row {
                anchors { left: parent.left; leftMargin: 26; verticalCenter: parent.verticalCenter }
                spacing: 11
                Glyph { name: "network"; color: Theme.peer; size: 17; stroke: 1.9; anchors.verticalCenter: parent.verticalCenter }
                Text { textFormat: Text.PlainText; text: "Folders shared by " + view.host.name + ". Open one to see what's inside."
                       color: Theme.peer; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font
                       width: Math.min(implicitWidth, view.width - 80); elide: Text.ElideRight }
            }
        }

        // its folders ride one connection per access, so they fail together: said once, for the computer
        Rectangle {
            id: strip
            visible: view.trouble
            width: parent.width; height: visible ? Math.max(66, said.implicitHeight + 24) : 0
            color: Theme.tint(view.retrying ? Theme.frostRgb : Theme.dangerRgb, .09)
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.tint(view.retrying ? Theme.frostRgb : Theme.dangerRgb, .22) }
            Glyph { id: sign; name: view.retrying ? "retry" : "warn"; color: view.retrying ? Theme.frostText : Theme.dangerText; size: 20; stroke: 1.9
                    anchors { left: parent.left; leftMargin: 26; verticalCenter: parent.verticalCenter } }
            Column {
                id: said
                anchors { left: sign.right; leftMargin: 12; right: again.visible ? again.left : parent.right; rightMargin: 20; verticalCenter: parent.verticalCenter }
                spacing: 3
                Text { textFormat: Text.PlainText; width: parent.width; wrapMode: Text.WordWrap; maximumLineCount: 2; elide: Text.ElideRight
                       text: view.retrying ? "Asking " + view.host.name + " for its folders again…" : Theme.cantOpen(view.host.name, view.failed.length === view.host.shares.length)
                       color: view.retrying ? Theme.frostText : Theme.dangerText; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font }
                Text { textFormat: Text.PlainText; visible: !view.retrying && text !== ""; width: parent.width; elide: Text.ElideRight
                       text: Theme.forAdmin(view.failed); color: Theme.dim; font.pixelSize: prefs.en; font.family: Theme.font }
            }
            ActionButton { id: again; visible: !view.retrying; compact: true; label: "Try again"; glyph: "retry"
                           anchors { right: parent.right; rightMargin: 26; verticalCenter: parent.verticalCenter }
                           onClicked: nav.retry(view.host) }
        }

        Item {
            width: parent.width
            height: parent.height - 80 - 46 - strip.height

            GridView {
                id: grid
                anchors { fill: parent; leftMargin: 26; rightMargin: 26; topMargin: 24; bottomMargin: 30 }
                clip: true
                boundsBehavior: Flickable.StopAtBounds
                readonly property int gap: 14
                readonly property int cols: Math.max(1, Math.floor((width + gap) / (178 + gap)))
                cellWidth: width / cols
                cellHeight: 196
                model: view.host.shares
                delegate: Item {
                    id: cell
                    required property var modelData
                    width: grid.cellWidth; height: grid.cellHeight
                    ShareTile {
                        width: grid.cellWidth - grid.gap; height: grid.cellHeight - grid.gap
                        share: cell.modelData
                        selected: nav.selShare === cell.modelData.id
                        opening: nav.opening === cell.modelData.id
                        onSelect: nav.selShare = cell.modelData.id
                        onOpen: { nav.selShare = cell.modelData.id; nav.openShare(cell.modelData) }
                    }
                }
            }
            ScrollLane { flick: grid; anchors { right: parent.right; rightMargin: 9; top: grid.top; bottom: grid.bottom } }
        }
    }
}
