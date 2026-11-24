import QtQuick
import "."

// the running copy or move, across the top of every screen once it has taken a second: what,
// where to, how far — and Cancel while it is still the copying
Rectangle {
    id: bar
    property string what: ""
    property string where: ""
    property bool late: false
    readonly property bool shown: transfer.busy && late
    readonly property real progress: transfer.total > 0 ? transfer.done / transfer.total : 0
    readonly property string label: transfer.stage === "cancel" ? "Cancelling…"
                                  : transfer.stage === "finish" ? "Finishing “" + what + "”…"
                                  : (transfer.moving ? "Moving “" : "Copying “") + what + "” to " + where + " · " + Math.round(progress * 100) + "%"

    Timer { id: slow; interval: 1000; onTriggered: bar.late = true }
    Connections { target: transfer; function onBusyChanged() { bar.late = false; if (transfer.busy) slow.restart(); else slow.stop() } }

    anchors { left: parent.left; right: parent.right; top: parent.top }
    height: shown ? 58 : 0
    visible: shown
    color: Theme.tint(Theme.frostRgb, .09)

    Row {
        anchors { left: parent.left; leftMargin: 26; verticalCenter: parent.verticalCenter }
        spacing: 11
        Glyph { name: transfer.moving ? "down" : "copy"; color: Theme.frost; size: 18; stroke: 1.9; anchors.verticalCenter: parent.verticalCenter }
        Text { textFormat: Text.PlainText; text: bar.label; color: Theme.frostText; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font
               width: Math.min(implicitWidth, bar.width - cancel.width - 110); elide: Text.ElideMiddle; anchors.verticalCenter: parent.verticalCenter }
    }
    ActionButton { id: cancel; compact: true; label: "Cancel"; glyph: "close"; tone: "plain"; visible: transfer.stage === "copy"
                   anchors { right: parent.right; rightMargin: 26; verticalCenter: parent.verticalCenter }
                   onClicked: transfer.cancel() }

    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 3; color: Theme.tint(Theme.frostRgb, .2) }
    Rectangle { anchors.bottom: parent.bottom; width: parent.width * (transfer.stage === "copy" ? bar.progress : 1); height: 3
                color: Theme.tint(Theme.frostRgb, transfer.stage === "copy" ? .88 : .45) }
}
