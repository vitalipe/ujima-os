import QtQuick
import "."

// IN A PLACE: toolbar · notice strip · the folder grid
Item {
    id: view
    property var nav
    // PlaceView stays instantiated at home, so bindings need a meta even with no place
    readonly property var m: nav.meta ? nav.meta : ({ glyph: "folder", color: "transparent", rgb: [0, 0, 0], notice: "", emptyHint: "" })
    readonly property bool hasSel: nav.selected !== ""
    readonly property bool holding: !!nav.held
    readonly property bool peer: !!nav.place && nav.place.kind === "peer"
    // the trail's room, shared out: where you are first, then the place, then a nearby folder's computer
    readonly property real room: Math.max(0, view.width - actions.width - back.width - 90)
    readonly property real fixed: (peer ? 123 : 48) + (nav.path.length ? 36 : 0)
    readonly property real folderW: nav.path.length ? Math.min(wide(folderM), 320, Math.max(0, room - fixed - (peer ? 100 : 60))) : 0
    readonly property real rootW: Math.min(wide(rootM), Math.max(40, room - fixed - folderW - (peer ? 40 : 0)))
    readonly property real compW: Math.min(wide(compM), Math.max(0, room - fixed - folderW - rootW))
    // measured apart from the labels: one cut to nothing no longer says how wide it would be
    TextMetrics { id: compM;   font: compT.font;   text: compT.text }
    TextMetrics { id: rootM;   font: rootT.font;   text: rootT.text }
    TextMetrics { id: folderM; font: folderT.font; text: folderT.text }
    function wide(m) { return Math.ceil(m.advanceWidth) + 2 }

    Column {
        anchors.fill: parent

        // toolbar
        Rectangle {
            width: parent.width; height: 80; color: "transparent"
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.hair }
            Row {
                anchors { left: parent.left; verticalCenter: parent.verticalCenter; leftMargin: 26 }
                spacing: 12
                ActionButton { id: back; label: "Back"; glyph: "back"; tone: "plain"; weight: Font.DemiBold; onClicked: nav.goBack() }
                Row {
                    spacing: 10
                    anchors.verticalCenter: parent.verticalCenter
                    Row {
                        visible: view.peer
                        spacing: 10
                        Rectangle {
                            height: 40; width: comp.implicitWidth + 30; radius: 11
                            color: Qt.rgba(1, 1, 1, .05)
                            Row {
                                id: comp; anchors.centerIn: parent; spacing: 9
                                Glyph { name: "computer"; color: Theme.peer; size: 18; stroke: 1.8; anchors.verticalCenter: parent.verticalCenter }
                                Text { id: compT; textFormat: Text.PlainText; text: view.peer ? nav.place.host : ""; color: Theme.text; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font
                                       elide: Text.ElideRight; width: view.compW }
                            }
                            HoverHandler { cursorShape: Qt.PointingHandCursor }
                            TapHandler { onTapped: nav.toComputer() }
                        }
                        Glyph { name: "crumb"; color: Theme.faint; size: 16; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
                    }
                    Rectangle {
                        height: 40; width: chip.implicitWidth + 30; radius: 11
                        color: Qt.rgba(1, 1, 1, nav.path.length ? .05 : .09)
                        Row {
                            id: chip; anchors.centerIn: parent; spacing: 9
                            Rectangle { visible: !view.peer; width: 9; height: 9; radius: 3; color: m.color; anchors.verticalCenter: parent.verticalCenter }
                            Text { id: rootT; textFormat: Text.PlainText; text: !nav.place ? "" : view.peer ? nav.place.label : nav.place.name; color: Theme.text; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font
                                   elide: Text.ElideRight; width: view.rootW }
                        }
                        HoverHandler { cursorShape: Qt.PointingHandCursor }
                        TapHandler { onTapped: nav.goRoot() }
                    }
                    Row {
                        visible: nav.path.length > 0
                        spacing: 10
                        anchors.verticalCenter: parent.verticalCenter
                        Glyph { name: "crumb"; color: Theme.faint; size: 16; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
                        Text { id: folderT; textFormat: Text.PlainText; text: nav.folderName; color: Theme.text; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font
                               elide: Text.ElideRight; width: view.folderW }
                    }
                }
            }
            Row {
                id: actions
                anchors { right: parent.right; verticalCenter: parent.verticalCenter; rightMargin: 26 }
                spacing: 9
                ActionButton { label: "Open";       glyph: "open";      tone: "primary"; enabled: view.hasSel; onClicked: nav.openSelected() }
                ActionButton { label: transfer.busy ? (transfer.moving ? "Moving…" : "Copying…") : view.holding ? "Holding 1" : "Take"
                               glyph: "hand"; tone: view.holding ? "held" : "plain"
                               enabled: !transfer.busy && (view.holding || view.hasSel); onClicked: view.holding ? nav.putBack() : nav.take() }
                ActionButton { visible: nav.canShare; label: "Share"; glyph: "share"; tone: "plain"; enabled: nav.selDir; onClicked: nav.askShare() }
                ActionButton { label: "New folder"; glyph: "newfolder"; tone: "plain";   enabled: nav.canWrite; onClicked: nav.ask("mkdir") }
                ActionButton { label: "Rename";     glyph: "rename";    tone: "plain";   enabled: view.hasSel && nav.canWrite; onClicked: nav.ask("rename") }
                ActionButton { label: "Delete";     glyph: "delete";    tone: "danger";  enabled: view.hasSel && nav.canWrite; onClicked: nav.ask("delete") }
            }
        }

        // place notice strip
        Rectangle {
            visible: m.notice !== ""
            width: parent.width; height: visible ? 46 : 0
            color: Theme.tint(m.rgb, .09)
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.tint(m.rgb, .2) }
            Row {
                anchors { left: parent.left; leftMargin: 26; verticalCenter: parent.verticalCenter }
                spacing: 11
                Glyph { name: m.noticeGlyph || m.glyph; color: m.color; size: 17; stroke: 1.9; anchors.verticalCenter: parent.verticalCenter }
                Text { textFormat: Text.PlainText; text: m.notice; color: m.color; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font
                       width: Math.min(implicitWidth, view.width - 80); elide: Text.ElideRight }
            }
        }

        // content
        Item {
            width: parent.width
            height: parent.height - 80 - (m.notice !== "" ? 46 : 0)

            // a read that takes a while says so; a quick one never flashes it
            Timer { id: slow; interval: 400; running: listing.loading }
            Text {
                visible: listing.loading && listing.count === 0 && !slow.running
                anchors { horizontalCenter: parent.horizontalCenter; top: parent.top; topMargin: 84 }
                text: "Loading…"; color: Theme.dim; font.pixelSize: prefs.sub; font.family: Theme.font
            }

            Column {
                visible: listing.count === 0 && !nav.pocket && !listing.loading
                anchors { horizontalCenter: parent.horizontalCenter; top: parent.top; topMargin: 94 }
                spacing: 0
                Rectangle {
                    width: 88; height: 88; radius: 24; color: Qt.rgba(1, 1, 1, .05)
                    anchors.horizontalCenter: parent.horizontalCenter
                    Glyph { anchors.centerIn: parent; name: "folder"; color: Theme.faint; size: 42; stroke: 1.5 }
                }
                Item { width: 1; height: 22 }
                Text { anchors.horizontalCenter: parent.horizontalCenter; text: "Nothing here yet"; color: Theme.text; font.pixelSize: prefs.title; font.weight: Font.Bold; font.family: Theme.font }
                Item { width: 1; height: 9 }
                Text { textFormat: Text.PlainText; anchors.horizontalCenter: parent.horizontalCenter; width: 420; horizontalAlignment: Text.AlignHCenter; wrapMode: Text.Wrap
                       text: m.emptyHint; color: Theme.dim; font.pixelSize: prefs.btn; lineHeight: 1.4; font.family: Theme.font }
            }

            GridView {
                id: grid
                anchors { fill: parent; leftMargin: 26; rightMargin: 26; topMargin: 24; bottomMargin: 30 }
                clip: true
                boundsBehavior: Flickable.StopAtBounds
                readonly property int gap: 14
                readonly property int cols: Math.max(1, Math.floor((width + gap) / (178 + gap)))
                cellWidth: width / cols
                cellHeight: 196
                model: listing
                delegate: Item {
                    id: cell
                    required property int index
                    required property string name
                    required property string type
                    required property string meta
                    readonly property bool pocket: type === "pocket"
                    width: grid.cellWidth; height: grid.cellHeight
                    Loader {
                        width: (cell.pocket ? grid.cellWidth * 2 : grid.cellWidth) - grid.gap
                        height: grid.cellHeight - grid.gap
                        sourceComponent: cell.pocket ? pocketC : cell.type === "pad" ? null : tileC
                    }
                    Component {
                        id: tileC
                        Tile {
                            name: cell.name; type: cell.type; meta: cell.meta
                            selected: nav.selected === cell.name
                            shared: !!nav.shares[nav.rel(cell.name)]
                            onSelect: nav.selected = cell.name
                            onOpen: nav.open(cell.index)
                        }
                    }
                    Component {
                        id: pocketC
                        PocketTile {
                            held: nav.held
                            busy: transfer.busy; moving: transfer.moving
                            progress: transfer.total > 0 ? transfer.done / transfer.total : 0
                            onCopy: nav.copyHere(); onMove: nav.moveHere(); onPutBack: nav.putBack()
                        }
                    }
                }
            }
            ScrollLane { flick: grid; anchors { right: parent.right; rightMargin: 9; top: grid.top; bottom: grid.bottom } }

            // right-click: on a tile = that item's menu (selecting it first), elsewhere = the folder's
            TapHandler {
                acceptedButtons: Qt.RightButton
                onTapped: (ep) => {
                    const g = parent.mapToItem(grid, ep.position.x, ep.position.y)
                    const i = grid.indexAt(g.x + grid.contentX, g.y + grid.contentY)
                    const d = i >= 0 ? grid.itemAtIndex(i) : null
                    let onTile = false
                    if (d && d.type !== "pocket" && d.type !== "pad") { const q = grid.mapToItem(d, g.x, g.y); onTile = q.x < d.width - grid.gap && q.y < d.height - grid.gap }
                    if (onTile) nav.selected = d.name
                    nav.openMenu(view.mapFromItem(null, ep.scenePosition.x, ep.scenePosition.y), onTile)
                }
            }
        }
    }

    ContextMenu { nav: view.nav }
}
