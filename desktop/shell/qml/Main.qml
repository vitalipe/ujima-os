import QtQuick
import QtQuick.Window
import "."

// The shell's windows, one process: the launcher and the lock card are managed (i3 keeps them on
// `1` and `lock`), the top bar and the dock are i3 docks (the host types them before they map),
// and the one tooltip card is theirs to share.
QtObject {
    property Window launcher: Launcher {}
    property Window tooltip:  Tooltip {}
    property Window topbar:   TopBar { tip: tooltip }
    property Window dock:     Dock { tip: tooltip }
    property Window lock:     Lock {}
}
