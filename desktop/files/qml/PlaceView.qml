import QtQuick
import "."

// IN A PLACE: toolbar · notice strip · the folder grid
Item {
    id: view
    property var nav
    // PlaceView stays instantiated at home, so bindings need a meta even with no place
    readonly property var m: nav.meta ? nav.meta : ({ glyph: "folder", color: "transparent", rgb: [0, 0, 0], notice: "", emptyHint: "" })
    readonly property bool hasSel: nav.selected !== ""

    Column {
        anchors.fill: parent

        // toolbar
        Rectangle {
            width: parent.width; height: 80; color: "transparent"
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: Theme.hair }
            Row {
                anchors { left: parent.left; verticalCenter: parent.verticalCenter; leftMargin: 26 }
                spacing: 12
                ActionButton { label: "Back"; glyph: "back"; tone: "plain"; weight: Font.DemiBold; onClicked: nav.goBack() }
                Row {
                    spacing: 10
                    anchors.verticalCenter: parent.verticalCenter
                    Rectangle {
                        height: 40; width: chip.implicitWidth + 30; radius: 11
                        color: Qt.rgba(1, 1, 1, nav.path.length ? .05 : .09)
                        Row {
                            id: chip; anchors.centerIn: parent; spacing: 9
                            Rectangle { width: 9; height: 9; radius: 3; color: m.color; anchors.verticalCenter: parent.verticalCenter }
                            Text { text: nav.place ? nav.place.name : ""; color: Theme.text; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font }
                        }
                        HoverHandler { cursorShape: Qt.PointingHandCursor }
                        TapHandler { onTapped: nav.goRoot() }
                    }
                    Row {
                        visible: nav.path.length > 0
                        spacing: 10
                        anchors.verticalCenter: parent.verticalCenter
                        Glyph { name: "crumb"; color: Theme.faint; size: 16; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
                        Text { text: nav.folderName; color: Theme.text; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font; elide: Text.ElideRight; width: Math.min(implicitWidth, 320) }
                    }
                }
            }
            Row {
                anchors { right: parent.right; verticalCenter: parent.verticalCenter; rightMargin: 26 }
                spacing: 9
                ActionButton { label: "Open";       glyph: "open";      tone: "primary"; enabled: view.hasSel; onClicked: nav.openSelected() }
                ActionButton { label: "New folder"; glyph: "newfolder"; tone: "plain";   onClicked: nav.ask("mkdir") }
                ActionButton { label: "Rename";     glyph: "rename";    tone: "plain";   enabled: view.hasSel; onClicked: nav.ask("rename") }
                ActionButton { label: "Delete";     glyph: "delete";    tone: "danger";  enabled: view.hasSel; onClicked: nav.ask("delete") }
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
                Glyph { name: m.glyph; color: m.color; size: 17; stroke: 1.9; anchors.verticalCenter: parent.verticalCenter }
                Text { text: m.notice; color: m.color; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font }
            }
        }

        // content
        Item {
            width: parent.width
            height: parent.height - 80 - (m.notice !== "" ? 46 : 0)

            Column {
                visible: listing.count === 0
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
                Text { anchors.horizontalCenter: parent.horizontalCenter; width: 420; horizontalAlignment: Text.AlignHCenter; wrapMode: Text.WordWrap
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
                    required property int index
                    required property string name
                    required property string type
                    required property string meta
                    width: grid.cellWidth; height: grid.cellHeight
                    Tile {
                        anchors { fill: parent; rightMargin: grid.gap; bottomMargin: grid.gap }
                        name: parent.name; type: parent.type; meta: parent.meta
                        selected: nav.selected === parent.name
                        onSelect: nav.selected = parent.name
                        onOpen: nav.open(parent.index)
                    }
                }
            }
            ScrollLane { flick: grid; anchors { right: parent.right; rightMargin: 9; top: grid.top; bottom: grid.bottom } }
        }
    }
}
