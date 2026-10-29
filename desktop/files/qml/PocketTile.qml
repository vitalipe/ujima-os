import QtQuick
import QtQuick.Shapes
import "."

// the item in your hand, at the front of every folder it could land in: its own icon, where it
// came from, and the two ways to put it down. × puts it back. While it is being put down here,
// the buttons give way to the bar.
Rectangle {
    id: pocket
    property var held
    property bool busy: false
    property bool moving: false
    property real progress: 0
    readonly property string type: held ? held.type : "file"
    signal copy()
    signal move()
    signal putBack()

    radius: 18
    color: Theme.tint(Theme.frostRgb, .06)
    Shape {
        anchors.fill: parent
        ShapePath {
            strokeColor: Theme.tint(Theme.frostRgb, .55); strokeWidth: 1.5; fillColor: "transparent"
            strokeStyle: ShapePath.DashLine; dashPattern: [4, 3]
            PathRectangle { x: 0.75; y: 0.75; width: pocket.width - 1.5; height: pocket.height - 1.5; radius: 18 }
        }
    }

    Rectangle {
        visible: !pocket.busy
        anchors { top: parent.top; right: parent.right; margins: 10 }
        width: 30; height: 30; radius: 9
        color: Qt.rgba(1, 1, 1, xh.hovered ? .13 : .07)
        Behavior on color { ColorAnimation { duration: 120 } }
        Glyph { anchors.centerIn: parent; name: "close"; color: Theme.dim; size: 15; stroke: 2.2 }
        HoverHandler { id: xh; cursorShape: Qt.PointingHandCursor }
        TapHandler { onTapped: pocket.putBack() }
    }

    Row {
        anchors { left: parent.left; right: parent.right; verticalCenter: parent.verticalCenter; leftMargin: 18; rightMargin: 18 }
        spacing: 18
        Rectangle {
            width: 62; height: 62; radius: 18
            anchors.verticalCenter: parent.verticalCenter
            opacity: .85
            color: pocket.type === "folder" ? Theme.tint([138, 176, 124], .14) : Qt.rgba(1, 1, 1, .05)
            Glyph { anchors.centerIn: parent; name: pocket.type; color: Theme.colorOf(pocket.type); size: 34; stroke: 1.6 }
        }
        Column {
            width: parent.width - 62 - 18
            anchors.verticalCenter: parent.verticalCenter
            spacing: 10
            Column {
                width: parent.width
                spacing: 2
                Text { width: parent.width - 30; text: pocket.held ? pocket.held.name : ""; color: Theme.text; font.pixelSize: prefs.name; font.weight: Font.Bold; font.family: Theme.font; elide: Text.ElideRight }
                Text { text: pocket.held ? "In your hand · from " + pocket.held.from : ""; color: Theme.frostText; font.pixelSize: prefs.en; font.family: Theme.font }
            }
            Row {
                visible: !pocket.busy
                spacing: 8
                ActionButton { compact: true; label: "Copy here"; glyph: "copy"; tone: "primary"; onClicked: pocket.copy() }
                ActionButton { compact: true; label: "Move here"; glyph: "down"; tone: "plain"; onClicked: pocket.move() }
            }
            Column {
                visible: pocket.busy
                width: parent.width - 12
                spacing: 8
                Text {
                    text: (pocket.moving ? "Moving here… " : "Copying here… ") + Math.round(pocket.progress * 100) + "%"
                    color: Theme.frostText; font.pixelSize: prefs.en + 1.5; font.weight: Font.DemiBold; font.family: Theme.font
                }
                Rectangle {
                    width: parent.width; height: 8; radius: 4
                    color: Qt.rgba(1, 1, 1, .08)
                    Rectangle { width: parent.width * pocket.progress; height: parent.height; radius: 4; color: Theme.tint(Theme.frostRgb, .88) }
                }
            }
        }
    }
}
