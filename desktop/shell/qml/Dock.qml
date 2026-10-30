import QtQuick
import QtQuick.Window
import "."

// The dock (v0.9 spec 33a): the launcher tile + the pinned actions on the left, the running apps
// centred, the tray on the right. A dock click is the same verb as a launcher tile: open = focus.
// An i3 dock: the host types it before it maps.
Window {
    id: dock
    required property var tip
    readonly property string currentId: desktop.current.id || ""

    title: "ujima-dock"
    flags: Qt.FramelessWindowHint | Qt.WindowDoesNotAcceptFocus
    x: 0; y: Screen.height - Theme.barBottom; width: Screen.width; height: Theme.barBottom
    color: Theme.panel
    visible: desktop.hasApps && !desktop.barsHidden

    Rectangle { width: parent.width; height: 1; color: Theme.panelBrd }

    // the tiles sit 8px off the bottom; centred on that row, so is the tray
    readonly property int rowY: height - 8 - 52
    readonly property int rowMid: rowY + 26

    // home + pinned: ACTIONS, not apps — lit only when their target is the view
    Row {
        x: 18; y: dock.rowY; spacing: 6
        DockTile {
            tip: dock.tip; label: "Apps"
            icon: Theme.icon("launcher.svg"); iconSize: 25
            active: dock.currentId === ""
            onClicked: desktop.home()
        }
        Repeater {
            model: desktop.pinned
            delegate: DockTile {
                required property var modelData
                tip: dock.tip; label: modelData.label; entry: modelData
                icon: modelData.icon; iconSize: 26
                active: modelData.id === dock.currentId
                onClicked: desktop.openApp(modelData.id)
            }
        }
    }

    // running apps, centred on the whole bar; system apps (files) are pinned on the left instead
    Row {
        anchors.horizontalCenter: parent.horizontalCenter
        y: dock.rowY; spacing: 6
        Repeater {
            model: desktop.running.filter(a => a.category !== "system")
            delegate: DockTile {
                required property var modelData
                tip: dock.tip; label: modelData.label; entry: modelData
                icon: modelData.icon; iconSize: 32
                app: true; category: modelData.category || ""
                active: modelData.id === dock.currentId
                onClicked: desktop.openApp(modelData.id)
            }
        }
    }

    // the tray: the controls, a hairline, then the clock — the one thing you cannot press
    Row {
        anchors.right: parent.right; anchors.rightMargin: 18
        y: dock.rowMid - height / 2; height: 32
        spacing: 4
        KbdControl { tip: dock.tip; anchors.verticalCenter: parent.verticalCenter }
        VolumeControl { tip: dock.tip; anchors.verticalCenter: parent.verticalCenter }
        Item {
            width: 4 + 1 + 6; height: 32
            Rectangle { x: 4; width: 1; height: 22; anchors.verticalCenter: parent.verticalCenter; color: Theme.white(.09) }
        }
        Text {
            anchors.verticalCenter: parent.verticalCenter
            rightPadding: 8
            text: Clock.time; color: Theme.dim
            font { family: Theme.font; pixelSize: 14 }
            Tip { tip: dock.tip; text: Clock.date }
        }
    }
}
