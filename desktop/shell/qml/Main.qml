import QtQuick
import QtQuick.Window
import "."

// The shell's windows, one process: the launcher and the lock card are managed (i3 keeps them on
// `1` and `lock`), the top bar and the dock are i3 docks (the host types them before they map),
// and the one tooltip card is theirs to share.
QtObject {
    id: shell
    property Window launcher: Launcher {}
    property Window tooltip:  Tooltip {}
    property Window topbar:   TopBar { tip: tooltip }
    property Window dock:     Dock { tip: tooltip }
    property Window lock:     Lock {}

    // the boot cover, once per X server: made last so it maps above the rest, gone once it has
    // played the outro over a ready desktop (bars up, launcher revealed)
    property Window cover: null
    property Component coverComponent: Component {
        Cover {
            ready: desktop.hasApps && desktop.hasCatalog && shell.launcher.revealed
            onFinished: { splash.done(); shell.cover = null; destroy() }
        }
    }
    Component.onCompleted: if (splash.cover) cover = coverComponent.createObject(null)
}
