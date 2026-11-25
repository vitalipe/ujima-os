import QtQuick
import QtQuick.Shapes
import "."

// HOME: "Where are your files?" — three tabs: this computer's ready places, one card each; the
// nearby computers that share folders, one card each; the folders this computer shares
Flickable {
    id: home
    signal enter(int row)
    signal openShare(var share)
    signal openComputer(var host)
    signal retry(var host)
    signal hush(string id)
    signal changeShare(var share)
    signal stopShare(var share)
    property string opening: ""       // the place id of the nearby share being opened
    property var hushed: ({})         // computers whose can't-open message was put away, by machine id
    property string retrying: ""      // the computer being asked again
    property string tab: "local"      // local | nearby | mine
    readonly property string tabLabel: tab === "nearby" ? "Nearby" : tab === "mine" ? "Shared from here" : "This computer"

    component SectionHead: Row {
        property string glyph: ""
        property color tint: Theme.text
        property string title: ""
        property string sub: ""
        spacing: 12
        Glyph { name: parent.glyph; color: parent.tint; size: 20; stroke: 1.8; anchors.verticalCenter: parent.verticalCenter }
        Text { textFormat: Text.PlainText; text: parent.title; color: Theme.text; font.pixelSize: prefs.name + 2.5; font.weight: Font.Bold; font.letterSpacing: -.2; font.family: Theme.font; anchors.verticalCenter: parent.verticalCenter }
        Text { textFormat: Text.PlainText; text: parent.sub; color: Theme.faint; font.pixelSize: prefs.sub - 1; font.family: Theme.font; anchors.verticalCenter: parent.verticalCenter }
    }
    contentHeight: body.implicitHeight + 64
    clip: true
    boundsBehavior: Flickable.StopAtBounds
    ScrollLane { flick: home; parent: home; anchors { right: parent.right; rightMargin: 9; top: parent.top; bottom: parent.bottom; topMargin: 34; bottomMargin: 30 } }

    Column {
        id: body
        anchors { left: parent.left; right: parent.right; top: parent.top; leftMargin: 46; rightMargin: 46; topMargin: 34 }
        spacing: 26

        Item {
            width: parent.width; height: head.implicitHeight
            Column {
                id: head
                spacing: 4
                Text { text: "Where are your files?"; color: Theme.text; font.pixelSize: prefs.title; font.weight: Font.Bold; font.letterSpacing: -.5; font.family: Theme.font }
                Text { text: "Choose a place to open it"; color: Theme.dim; font.pixelSize: prefs.sub; font.family: Theme.font }
            }
            Rectangle {
                anchors.right: parent.right; anchors.bottom: parent.bottom
                height: hint.implicitHeight + 18; width: hintRow.implicitWidth + 28; radius: 999
                color: Qt.rgba(1, 1, 1, .05); border.width: 1; border.color: Qt.rgba(1, 1, 1, .08)
                Row {
                    id: hintRow; anchors.centerIn: parent; spacing: 8
                    Glyph { name: "plus"; color: Theme.frost; size: 14; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
                    Text { id: hint; text: "Plug in a USB stick and it shows up here"; color: Theme.dim; font.pixelSize: prefs.en; font.weight: Font.DemiBold; font.family: Theme.font }
                }
            }
        }

        Rectangle {
            width: tabs.implicitWidth + 12; height: 64; radius: 16
            color: Qt.rgba(0, 0, 0, .22)
            border.width: 1; border.color: Qt.rgba(1, 1, 1, .05)
            Row {
                id: tabs
                anchors.centerIn: parent
                spacing: 8
                TabButton { label: "This computer"; glyph: "computer"; tint: "#8ab07c"; rgb: [138, 176, 124]
                            count: places.count; on: home.tab === "local"; onClicked: home.tab = "local" }
                TabButton { label: "Nearby"; glyph: "network"; tint: Theme.peer; rgb: Theme.peerRgb
                            count: places.nearbyCount; on: home.tab === "nearby"; onClicked: home.tab = "nearby" }
                TabButton { label: "Shared from here"; glyph: "share"; tint: "#8ab07c"; rgb: [138, 176, 124]
                            count: places.shared.length; on: home.tab === "mine"; onClicked: home.tab = "mine" }
            }
        }

        Column {
            visible: home.tab === "local"
            width: parent.width
            spacing: 14

            SectionHead { glyph: "computer"; tint: "#8ab07c"; title: "On this computer"; sub: "Storage and USB sticks plugged in here" }

            Grid {
                id: grid
                width: parent.width
                columns: 2
                columnSpacing: 18; rowSpacing: 18
                readonly property real cellW: (width - columnSpacing) / 2
                Repeater {
                    model: places
                    delegate: PlaceCard {
                        required property int index
                        required property var model
                        width: grid.cellW
                        place: model
                        onOpen: home.enter(index)
                    }
                }
            }

            Text {
                textFormat: Text.PlainText
                visible: places.count === 0
                width: parent.width
                text: places.connected ? "No place is ready yet." : "Waiting for ujimad…"
                color: Theme.faint; font.pixelSize: prefs.sub; font.family: Theme.font
            }
        }

        Column {
            visible: home.tab === "nearby"
            width: parent.width
            spacing: 14

            SectionHead {
                glyph: "network"; tint: Theme.peer; title: "From computers nearby"
                readonly property int folders: places.nearbyCount
                readonly property int computers: places.nearby.length
                sub: !places.connected ? "Looking…"
                   : folders === 0 ? "Nothing found yet"
                   : folders + (folders === 1 ? " shared folder on " : " shared folders on ") + computers + (computers === 1 ? " computer" : " computers")
            }

            Grid {
                id: nearby
                width: parent.width
                // the design's 380 px minimum, grown with the text size so a folder's name keeps its room
                readonly property real minW: 380 * prefs.name / 15.5
                columns: Math.max(1, Math.floor((width + 18) / (minW + 18)))
                columnSpacing: 18; rowSpacing: 18
                readonly property real cellW: (width - columnSpacing * (columns - 1)) / columns

                Repeater {
                    model: places.nearby
                    delegate: ComputerCard {
                        required property var modelData
                        width: nearby.cellW
                        host: modelData
                        opening: home.opening
                        hushed: !!home.hushed[modelData.id]
                        retrying: home.retrying === modelData.id
                        onOpen: (share) => home.openShare(share)
                        onEnter: home.openComputer(modelData)
                        onRetry: home.retry(modelData)
                        onHush: home.hush(modelData.id)
                    }
                }

                // before the first word from ujimad
                Rectangle {
                    visible: !places.connected
                    width: nearby.cellW; height: looking.implicitHeight + 46; radius: 18
                    color: Qt.rgba(1, 1, 1, .02)
                    border.width: 1.5; border.color: Theme.tint(Theme.peerRgb, .2)
                    Column {
                        id: looking
                        anchors { left: parent.left; right: parent.right; top: parent.top; topMargin: 24; leftMargin: 26; rightMargin: 26 }
                        spacing: 18
                        Row {
                            width: parent.width
                            spacing: 18
                            Rectangle {
                                width: 62; height: 62; radius: 18
                                anchors.verticalCenter: parent.verticalCenter
                                color: Theme.tint(Theme.peerRgb, .07); border.width: 1; border.color: Theme.tint(Theme.peerRgb, .24)
                                Glyph { anchors.centerIn: parent; name: "network"; color: Theme.peer; size: 30; stroke: 1.6 }
                            }
                            Column {
                                width: parent.width - 62 - 18
                                anchors.verticalCenter: parent.verticalCenter
                                spacing: 4
                                Text { width: parent.width; text: "Looking nearby…"; color: "#c2cad6"; font.pixelSize: prefs.card; font.weight: Font.Bold; font.letterSpacing: -.3; font.family: Theme.font }
                                Text { width: parent.width; wrapMode: Text.WordWrap; text: "Checking other computers for shared folders"; color: Theme.dim; font.pixelSize: prefs.sub - 1; font.family: Theme.font }
                            }
                        }
                        Rectangle {
                            id: bar
                            width: parent.width; height: 10; radius: 999; clip: true
                            color: Qt.rgba(1, 1, 1, .07)
                            Row {
                                Repeater {
                                    model: Math.max(0, Math.ceil(bar.width / 28))
                                    Item { width: 28; height: 10; Rectangle { width: 14; height: 10; color: Theme.tint(Theme.peerRgb, .38) } }
                                }
                            }
                        }
                    }
                }

                Item {
                    id: none
                    visible: places.connected && places.nearbyCount === 0
                    width: nearby.cellW; height: Math.max(62, noneText.implicitHeight) + 48
                    Shape {
                        anchors.fill: parent
                        ShapePath {
                            strokeColor: Qt.rgba(1, 1, 1, .13); strokeWidth: 1.5; fillColor: "transparent"
                            strokeStyle: ShapePath.DashLine; dashPattern: [4, 3]
                            PathRectangle { x: 0.75; y: 0.75; width: none.width - 1.5; height: none.height - 1.5; radius: 18 }
                        }
                    }
                    Rectangle {
                        id: noneIcon
                        anchors { left: parent.left; leftMargin: 26; verticalCenter: parent.verticalCenter }
                        width: 62; height: 62; radius: 18
                        color: Qt.rgba(1, 1, 1, .04)
                        Glyph { anchors.centerIn: parent; name: "network"; color: Theme.faint; size: 30; stroke: 1.6 }
                    }
                    Column {
                        id: noneText
                        anchors { left: noneIcon.right; leftMargin: 18; right: parent.right; rightMargin: 26; verticalCenter: parent.verticalCenter }
                        spacing: 4
                        Text { width: parent.width; text: "No shared folders nearby"; color: "#c2cad6"; font.pixelSize: prefs.name + 2.5; font.weight: Font.Bold; font.family: Theme.font }
                        Text { width: parent.width; wrapMode: Text.WordWrap; lineHeight: 1.4; color: Theme.dim; font.pixelSize: prefs.sub - 1; font.family: Theme.font
                               text: "When another computer nearby shares a folder, it shows up here." }
                    }
                }
            }
        }

        Column {
            visible: home.tab === "mine"
            width: parent.width
            spacing: 14

            SectionHead { glyph: "share"; tint: Theme.peer; title: "Shared from this computer"; sub: "Computers nearby see these on " + machine.name }

            Item {
                id: nothing
                visible: places.shared.length === 0
                width: parent.width; height: nothingText.implicitHeight + 44
                Shape {
                    anchors.fill: parent
                    ShapePath {
                        strokeColor: Qt.rgba(1, 1, 1, .12); strokeWidth: 1.5; fillColor: "transparent"
                        strokeStyle: ShapePath.DashLine; dashPattern: [4, 3]
                        PathRectangle { x: 0.75; y: 0.75; width: nothing.width - 1.5; height: nothing.height - 1.5; radius: 16 }
                    }
                }
                Text {
                    id: nothingText
                    anchors { left: parent.left; right: parent.right; verticalCenter: parent.verticalCenter; verticalCenterOffset: 3; leftMargin: 24; rightMargin: 24 }
                    textFormat: Text.StyledText; wrapMode: Text.WordWrap; lineHeight: 1.4
                    color: Theme.dim; font.pixelSize: prefs.btn; font.family: Theme.font
                    text: "You're not sharing anything. Open <font color=\"#e7eaef\"><b>This Computer</b></font>, pick a folder, then choose <font color=\"#e7eaef\"><b>Share</b></font>."
                }
            }

            Rectangle {
                visible: places.shared.length > 0
                width: parent.width; height: mine.implicitHeight; radius: 16
                color: Qt.rgba(1, 1, 1, .025)
                border.width: 1; border.color: Qt.rgba(1, 1, 1, .07)
                Column {
                    id: mine
                    width: parent.width
                    Repeater {
                        model: places.shared
                        delegate: Item {
                            id: row
                            required property var modelData
                            required property int index
                            readonly property var access: Theme.accessChip(modelData.access)
                            width: mine.width; height: Math.max(48, names.implicitHeight) + 30
                            Rectangle { visible: row.index > 0; width: parent.width; height: 1; color: Theme.brd }
                            Rectangle {
                                id: icon
                                anchors { left: parent.left; leftMargin: 20; verticalCenter: parent.verticalCenter }
                                width: 48; height: 48; radius: 13
                                color: Qt.rgba(1, 1, 1, .05)
                                Glyph { anchors.centerIn: parent; name: "folder"; color: Theme.colorOf("folder"); size: 25; stroke: 1.6 }
                            }
                            Row {
                                id: actions
                                anchors { right: parent.right; rightMargin: 20; verticalCenter: parent.verticalCenter }
                                spacing: 8
                                ActionButton { compact: true; label: "Change"; glyph: "rename"; tone: "plain"; onClicked: home.changeShare(row.modelData) }
                                ActionButton { compact: true; label: "Stop sharing"; glyph: "close"; tone: "danger"; onClicked: home.stopShare(row.modelData) }
                            }
                            Chip { id: chip; anchors { right: actions.left; rightMargin: 16; verticalCenter: parent.verticalCenter }
                                   text: row.access.text; glyph: row.access.glyph; tone: row.access.tone }
                            Column {
                                id: names
                                anchors { left: icon.right; leftMargin: 16; right: chip.left; rightMargin: 16; verticalCenter: parent.verticalCenter }
                                spacing: 3
                                Text { textFormat: Text.PlainText; width: parent.width; text: row.modelData.name; color: Theme.text; elide: Text.ElideRight
                                       font.pixelSize: prefs.name + .5; font.weight: Font.Bold; font.family: Theme.font }
                                Text { textFormat: Text.PlainText; width: parent.width; color: Theme.dim; elide: Text.ElideMiddle; font.pixelSize: prefs.en + .5; font.family: Theme.font
                                       text: row.modelData.placeName + " › " + row.modelData.path.split("/").join(" › ") }
                            }
                        }
                    }
                }
            }
        }
    }
}
