import QtQuick
import "."

// one nearby computer, the same size whatever it shares: its name, then up to three of its folders, or
// two and how many more. A folder row opens that folder; the rest of the card opens the computer.
// Folders that can't be opened fail together, so the card says it once, in place of the rows
Rectangle {
    id: card
    property var host                 // {id, name, rw, shares} from places.nearby
    property string opening: ""       // the place id of the share being opened
    property bool hushed: false       // its can't-open message was put away
    property bool retrying: false
    signal open(var share)
    signal enter()
    signal retry()
    signal hush()

    readonly property int n: host.shares.length
    readonly property int shown: n > 3 ? 2 : n
    readonly property var failed: host.shares.filter((s) => s.state === "invalid" && s.id !== card.opening)
    readonly property bool trouble: retrying || failed.length > 0 && !hushed
    property int hot: -1              // the folder row under the pointer
    readonly property bool lit: ch.hovered && hot < 0 && !ph.hovered

    height: 256
    radius: 18
    color: Qt.rgba(1, 1, 1, lit ? .06 : .03)
    border.width: lit ? 2 : 1.5
    border.color: Theme.tint(Theme.peerRgb, lit ? (tap.pressed ? 1 : .8) : .3)
    Behavior on color { ColorAnimation { duration: 160 } }
    HoverHandler { id: ch; cursorShape: Qt.PointingHandCursor }
    TapHandler { id: tap; onTapped: card.enter() }

    Item {
        id: head
        anchors { left: parent.left; right: parent.right; top: parent.top; topMargin: 20; leftMargin: 22; rightMargin: 22 }
        height: 60
        Rectangle {
            id: icon
            width: 48; height: 48; radius: 14
            color: Theme.tint(Theme.peerRgb, .14)
            border.width: 1; border.color: Theme.tint(Theme.peerRgb, .3)
            Glyph { anchors.centerIn: parent; name: "computer"; color: Theme.peer; size: 26; stroke: 1.6 }
        }
        Glyph { name: "chevron"; color: Theme.peer; size: 22; stroke: 2; anchors { right: parent.right; top: parent.top; topMargin: 2 } }
        Column {
            anchors { left: icon.right; leftMargin: 14; right: parent.right; verticalCenter: icon.verticalCenter }
            spacing: 3
            // computers of one name arrive with a tail that tells them apart, after the only space a name can have: the tail leads
            Row {
                id: name
                readonly property int cut: card.host.name.lastIndexOf(" ")
                width: parent.width - 32
                spacing: cut > 0 ? 7 : 0
                Text { textFormat: Text.PlainText; visible: name.cut > 0; text: card.host.name.slice(0, Math.max(0, name.cut)); color: Theme.dim; elide: Text.ElideRight
                       width: Math.min(implicitWidth, Math.max(0, name.width - tail.width - 7))
                       font.pixelSize: prefs.card; font.weight: Font.DemiBold; font.letterSpacing: -.3; font.family: Theme.font }
                Text { id: tail; textFormat: Text.PlainText; text: card.host.name.slice(name.cut + 1); color: Theme.text; elide: Text.ElideRight
                       width: Math.min(implicitWidth, name.width)
                       font.pixelSize: prefs.card; font.weight: Font.Bold; font.letterSpacing: -.3; font.family: Theme.font }
            }
            Text { textFormat: Text.PlainText; width: parent.width; color: Theme.dim; elide: Text.ElideRight; font.pixelSize: prefs.sub; font.family: Theme.font
                   text: card.n + (card.n === 1 ? " shared folder" : " shared folders")
                         + (card.host.rw ? " · " + card.host.rw + " you can change" : " · view only") }
        }
    }

    Column {
        id: list
        visible: !card.trouble
        anchors { left: head.left; right: head.right; top: head.bottom }
        spacing: 6

        Repeater {
            model: card.host.shares.slice(0, card.shown)
            delegate: Rectangle {
                id: row
                required property var modelData
                required property int index
                readonly property bool opening: card.opening === modelData.id
                readonly property bool failed: modelData.state === "invalid" && !opening
                readonly property var access: Theme.accessChip(modelData.access)
                width: list.width; height: 50; radius: 12
                color: opening ? Theme.tint(Theme.frostRgb, .08) : Qt.rgba(1, 1, 1, rh.hovered ? .1 : .045)
                border.width: 1
                border.color: opening ? Theme.tint(Theme.frostRgb, .45)
                            : rt.pressed ? Theme.tint(Theme.peerRgb, .9) : Qt.rgba(1, 1, 1, rh.hovered ? .22 : .07)
                Behavior on color { ColorAnimation { duration: 160 } }

                Glyph { id: folder; name: "folder"; color: Theme.peer; size: 24; stroke: 1.7
                        anchors { left: parent.left; leftMargin: 13; verticalCenter: parent.verticalCenter } }
                Glyph { id: arrow; name: "chevron"; color: Theme.peer; size: 20; stroke: 2; visible: !row.opening
                        anchors { right: parent.right; rightMargin: 12; verticalCenter: parent.verticalCenter } }
                Chip {
                    id: chip
                    anchors { right: row.opening ? parent.right : arrow.left; rightMargin: 12; verticalCenter: parent.verticalCenter }
                    text:  row.opening ? "Opening…" : row.failed ? "Can't open" : row.access.text
                    glyph: row.opening ? "open"     : row.failed ? "warn"       : row.access.glyph
                    tone:  row.opening ? "frost"    : row.failed ? "danger"     : row.access.tone
                }
                Text { textFormat: Text.PlainText; anchors { left: folder.right; leftMargin: 12; right: chip.left; rightMargin: 12; verticalCenter: parent.verticalCenter }
                       text: row.modelData.label; color: Theme.text; elide: Text.ElideRight
                       font.pixelSize: prefs.name + 1.5; font.weight: Font.DemiBold; font.family: Theme.font }

                HoverHandler { id: rh; cursorShape: Qt.PointingHandCursor
                               onHoveredChanged: if (hovered) card.hot = row.index; else if (card.hot === row.index) card.hot = -1 }
                // takes the grab, so the card never sees a tap on a row
                TapHandler { id: rt; gesturePolicy: TapHandler.ReleaseWithinBounds; onTapped: if (!row.opening) card.open(row.modelData) }
            }
        }

        Item {
            visible: card.n > card.shown
            width: list.width; height: 50
            Glyph { id: dots; name: "more"; color: Theme.peer; size: 22; stroke: 2.6
                    anchors { left: parent.left; leftMargin: 14; verticalCenter: parent.verticalCenter } }
            Text { anchors { left: dots.right; leftMargin: 12; verticalCenter: parent.verticalCenter }
                   text: "+" + (card.n - card.shown) + " more folders"; color: "#c9a9d8"
                   font.pixelSize: prefs.name; font.weight: Font.DemiBold; font.family: Theme.font }
        }
    }

    Rectangle {
        id: panel
        visible: card.trouble
        anchors { left: head.left; right: head.right; top: head.bottom }
        height: 162; radius: 12; clip: true
        color: Theme.tint(card.retrying ? Theme.frostRgb : Theme.dangerRgb, .07)
        border.width: 1; border.color: Theme.tint(card.retrying ? Theme.frostRgb : Theme.dangerRgb, .3)
        HoverHandler { id: ph; cursorShape: Qt.ArrowCursor }
        TapHandler { gesturePolicy: TapHandler.ReleaseWithinBounds }   // a tap on the message is not a tap on the card

        Column {
            anchors { left: parent.left; right: parent.right; top: parent.top; margins: 12 }
            spacing: 6
            Chip { text: card.retrying ? "Trying again…" : "Can't open"; glyph: card.retrying ? "retry" : "warn"; tone: card.retrying ? "frost" : "danger" }
            Text { textFormat: Text.PlainText; width: parent.width; wrapMode: Text.Wrap; maximumLineCount: 2; elide: Text.ElideRight; lineHeight: 1.3
                   text: card.retrying ? "Asking " + card.host.name + " for its folders again." : Theme.cantOpen(card.host.name, card.failed.length === card.n)
                   color: Theme.text; font.pixelSize: prefs.en + 1; font.family: Theme.font }
            Text { textFormat: Text.PlainText; visible: !card.retrying && text !== ""; width: parent.width; elide: Text.ElideRight
                   text: Theme.forAdmin(card.failed); color: Theme.dim; font.pixelSize: prefs.en; font.family: Theme.font }
        }
        Row {
            visible: !card.retrying
            anchors { left: parent.left; bottom: parent.bottom; leftMargin: 12; bottomMargin: 11 }
            spacing: 8
            ActionButton { compact: true; label: "Try again"; glyph: "retry"; onClicked: card.retry() }
            ActionButton { compact: true; label: "OK"; onClicked: card.hush() }
        }
    }
}
