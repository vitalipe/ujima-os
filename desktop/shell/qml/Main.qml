import QtQuick
import QtQuick.Window
import "."

// The shell's windows, one process. The launcher is a managed window i3 keeps on `1`.
QtObject {
    property Window launcher: Launcher {}
}
