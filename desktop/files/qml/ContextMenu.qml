import QtQuick
import "."

// the right-click menu: a card at the pointer, rows of glyph + label + chord hint. Pure view of
// nav's menu state; a row runs its verb through nav.act
Item {
    id: menu
    property var nav
    readonly property int rowH: Math.round(prefs.btn * 2.75)
    anchors.fill: parent
    visible: nav.menuOpen
    onVisibleChanged: if (visible) fade.restart()
    z: 50

    TapHandler { acceptedButtons: Qt.AllButtons; onTapped: nav.menuOpen = false }   // click outside = close

    Rectangle {   // a dark ring where the design would cast a shadow
        x: card.x - 1; y: card.y - 1; width: card.width + 2; height: card.height + 2; radius: 13
        color: Qt.rgba(0, 0, 0, .4)
    }
    Rectangle {
        id: card
        readonly property int pad: 6
        x: Math.max(8, Math.min(nav.menuAt.x, menu.width - width - 8))
        y: Math.max(8, Math.min(nav.menuAt.y, menu.height - height - 8))
        width: Math.round(prefs.btn * 15)
        height: col.implicitHeight + pad * 2
        radius: 12
        color: Theme.panel
        border.width: 1; border.color: Theme.brd
        NumberAnimation on opacity { id: fade; from: 0; to: 1; duration: 120; running: false }
        TapHandler { acceptedButtons: Qt.AllButtons; gesturePolicy: TapHandler.ReleaseWithinBounds }   // takes the grab, so the scrim never sees a tap on the card

        Column {
            id: col
            anchors { left: parent.left; right: parent.right; top: parent.top; margins: card.pad }
            Repeater {
                model: nav.menuItems
                delegate: Rectangle {
                    id: row
                    required property var modelData
                    readonly property bool sep: !!modelData.sep
                    readonly property bool on: modelData.enabled !== false
                    readonly property bool danger: !!modelData.danger
                    readonly property color fg: !on ? Theme.faint : danger ? Theme.dangerText : Theme.text
                    width: col.width; height: sep ? 13 : menu.rowH; radius: 8
                    color: !sep && on && rh.hovered ? (danger ? Theme.tint(Theme.dangerRgb, .14) : Qt.rgba(1, 1, 1, .07)) : "transparent"
                    Behavior on color { ColorAnimation { duration: 120 } }
                    Rectangle { visible: row.sep; anchors.centerIn: parent; width: parent.width - 8; height: 1; color: Theme.hair }
                    Row {
                        visible: !row.sep
                        anchors { left: parent.left; leftMargin: 10; verticalCenter: parent.verticalCenter }
                        spacing: 10
                        Glyph { name: row.modelData.glyph || "file"; color: row.fg; size: 18; stroke: 1.8; anchors.verticalCenter: parent.verticalCenter }
                        Text { text: row.modelData.label || ""; color: row.fg; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font; anchors.verticalCenter: parent.verticalCenter }
                    }
                    Text {
                        visible: !row.sep && !!row.modelData.hint
                        anchors { right: parent.right; rightMargin: 12; verticalCenter: parent.verticalCenter }
                        text: row.modelData.hint || ""; color: Theme.faint; font.pixelSize: prefs.en; font.family: Theme.font
                    }
                    HoverHandler { id: rh; enabled: !row.sep && row.on; cursorShape: Qt.PointingHandCursor }
                    TapHandler { enabled: !row.sep && row.on; onTapped: { nav.menuOpen = false; nav.act(row.modelData.act) } }
                }
            }
        }
    }
}
