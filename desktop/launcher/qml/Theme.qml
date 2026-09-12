pragma Singleton
import QtQuick

// The shell's tokens (launcher.css of design turn 47a) and the category presentation.
QtObject {
    readonly property color bg:    "#14171b"
    readonly property color text:  "#e7eaef"
    readonly property color dim:   "#9aa4b2"
    readonly property color faint: "#697180"
    readonly property color frost: "#88c0d0"
    readonly property string font: "Public Sans"

    // category presentation only: display order, label, colour — which apps belong, their icons
    // and labels are the catalog's. Keys and colours match the dock's rings (eww.scss).
    // 'system' is deliberately absent: its apps (files, console) are pinned in the dock, never tiles.
    readonly property var categories: [
        { key: "learn",   label: "LEARN",   rgb: [203, 168, 120] },
        { key: "explore", label: "EXPLORE", rgb: [126, 158, 214] },
        { key: "office",  label: "OFFICE",  rgb: [181, 143, 201] },
        { key: "create",  label: "CREATE",  rgb: [206, 147, 166] },
        { key: "code",    label: "CODE",    rgb: [176, 184, 119] } ]

    function tint(rgb, a) { return Qt.rgba(rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, a) }
}
