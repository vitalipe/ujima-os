pragma Singleton
import QtQuick

// The time the dock and the lock card read: HH:mm, and the long date for the clock's tooltip.
QtObject {
    property date now: new Date()
    readonly property string time: Qt.formatTime(now, "HH:mm")
    readonly property string date: Qt.formatDate(now, "dddd, MMMM d")

    property Timer tick: Timer { interval: 1000; running: true; repeat: true; onTriggered: now = new Date() }
}
