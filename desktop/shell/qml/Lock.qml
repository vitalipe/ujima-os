import QtQuick
import QtQuick.Window
import "."

// The lock card: opened once and left on the `lock` workspace (an i3 assign puts it there), so
// locking is a workspace switch — nothing maps or tears down. See-through: the wallpaper on the
// X root is already there.
Window {
    title: "Lock"
    visible: true
    width: 1280; height: 800
    color: "transparent"

    Column {
        anchors.centerIn: parent
        spacing: 27       // GTK's label boxes run a pixel taller than Qt's
        Rectangle {
            anchors.horizontalCenter: parent.horizontalCenter
            width: 96; height: 96; radius: 28
            color: Theme.tint([136, 192, 208], .10)
            border { width: 1.5; color: Theme.tint([136, 192, 208], .28) }
            Image {
                anchors.centerIn: parent
                width: 46; height: 46; sourceSize: Qt.size(46, 46)
                source: Theme.icon("lock.svg")
            }
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "Locked"; color: Theme.text
            font { family: Theme.font; pixelSize: 40; weight: Font.DemiBold }
        }
        Column {
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: 5
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: Clock.time; color: Theme.text
                font { family: Theme.font; pixelSize: 22; weight: Font.DemiBold }
            }
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: Clock.date; color: Theme.faint
                font { family: Theme.font; pixelSize: 13; weight: Font.Medium }
            }
        }
    }
}
