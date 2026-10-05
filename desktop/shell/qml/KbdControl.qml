import QtQuick
import "."

// The keyboard layout: the code, then the icon as the fixed right anchor. Hovering slides the
// other layouts out to the LEFT, each a direct pick (one layout = nothing to choose, no reveal);
// a click on the code cycles, like super+space. Pinned by an activity: a padlock, no reveal, inert.
Item {
    id: k
    required property var tip
    readonly property var kb: desktop.keyboard
    readonly property string current: (kb.layout && kb.layout.value) || "--"
    readonly property bool locked: !!(kb.layout && kb.layout.locked)
    readonly property var layouts: kb.layouts || []
    property bool picked: false                       // a pick collapses it until the pointer leaves
    readonly property bool open: hover.hovered && !locked && !picked
    readonly property bool reveal: open && layouts.length > 1
    readonly property int margin: open ? 4 : 0       // the open well pushes its left neighbours

    implicitWidth: margin + box.width
    implicitHeight: 32

    Item {
        id: box
        x: k.margin
        width: alts.width + 2 + code.width; height: 32
        HoverHandler { id: hover; onHoveredChanged: if (!hovered) k.picked = false }

        // open, the alternatives and the code are one control: a shallow well says so
        Rectangle {
            anchors.fill: parent; radius: height / 2
            color: k.open ? Qt.rgba(0, 0, 0, .22) : "transparent"
            Behavior on color { ColorAnimation { duration: 160 } }
        }

        Item {
            id: alts
            width: k.reveal ? row.implicitWidth : 0; height: 32; clip: true
            Behavior on width { NumberAnimation { duration: 180; easing.type: Easing.OutCubic } }
            Row {
                id: row
                anchors.right: parent.right; height: 32
                leftPadding: 6; spacing: 2
                Repeater {
                    model: k.layouts
                    // every layout renders, the current one hidden — collapsed, no gap
                    delegate: Text {
                        required property string modelData
                        visible: modelData !== k.current
                        anchors.verticalCenter: parent.verticalCenter
                        leftPadding: 9; rightPadding: 9; topPadding: 6; bottomPadding: 6
                        text: modelData
                        color: altHover.hovered ? Theme.text : Theme.faint
                        Behavior on color { ColorAnimation { duration: 150 } }
                        font { family: Theme.font; pixelSize: 14; weight: Font.Medium }
                        Tip { id: altHover; tip: k.tip; text: "Switch to " + modelData }
                        // collapse before posting: picking is a decision, not a round-trip
                        TapHandler { onTapped: { k.picked = true; desktop.setLayout(modelData) } }
                    }
                }
            }
        }

        // the code then the icon: the icon is the fixed right anchor, like the speaker's
        Item {
            id: code
            x: alts.width + 2
            width: 8 + label.implicitWidth + 7 + (k.locked ? 4 : 0) + 20 + 8; height: 32
            Text {
                id: label
                x: 8; anchors.verticalCenter: parent.verticalCenter
                text: k.current; color: Theme.dim
                font { family: Theme.font; pixelSize: 14; weight: Font.Medium }
            }
            Image {
                x: label.x + label.implicitWidth + 7; y: 6
                width: 10; height: 10; sourceSize: Qt.size(10, 10)
                source: Theme.icon("lockmark.svg")
                visible: k.locked
            }
            Image {
                x: parent.width - 8 - 20; anchors.verticalCenter: parent.verticalCenter
                width: 20; height: 20; sourceSize: Qt.size(20, 20)
                source: Theme.icon("keyboard.svg")
            }
            Tip { tip: k.tip; text: k.locked ? "Keyboard layout is locked" : "Keyboard layout" }
            // posts the :next the server showed — idempotent, a double click cannot double-advance
            TapHandler { enabled: !k.locked; onTapped: if (k.kb.next) desktop.setLayout(k.kb.next) }
        }
    }
}
