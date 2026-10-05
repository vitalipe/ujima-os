pragma Singleton
import QtQuick

// The shell's tokens (launcher.css of design turn 47a, the bars' v0.9 skin) and the category
// presentation. The RULE: no colour = at rest, the category colour = on/active.
QtObject {
    readonly property color bg:       "#14171b"
    readonly property color panel:    "#1b2027"   // the top pane and the dock
    readonly property color panelBrd: "#282e38"
    readonly property color text:     "#e7eaef"
    readonly property color dim:      "#9aa4b2"
    readonly property color faint:    "#697180"
    readonly property color frost:    "#88c0d0"
    readonly property color danger:   "#d68383"
    readonly property string font:    "Public Sans"

    // the bars' heights — i3 docks them, so the band between is what is left
    readonly property int barTop:    48
    readonly property int barBottom: 68

    // category presentation only: display order, label, colour — which apps belong, their icons
    // and labels are the catalog's. 'system' is absent: its apps (files, console) are pinned in
    // the dock, never tiles.
    readonly property var categories: [
        { key: "learn",   label: "LEARN",   rgb: [203, 168, 120] },
        { key: "explore", label: "EXPLORE", rgb: [126, 158, 214] },
        { key: "office",  label: "OFFICE",  rgb: [181, 143, 201] },
        { key: "create",  label: "CREATE",  rgb: [206, 147, 166] },
        { key: "code",    label: "CODE",    rgb: [176, 184, 119] } ]

    // every category the dock can tint, the pinned ones included
    readonly property var categoryRgb: ({
        learn: [203, 168, 120], explore: [126, 158, 214], office: [181, 143, 201],
        create: [206, 147, 166], code: [176, 184, 119], system: [136, 192, 208],
        "web-files": [134, 184, 126] })

    // the shell's own glyphs ship beside its QML (desktop/shell/icons); app icons come off the wire
    function icon(name) { return Qt.resolvedUrl("../icons/" + name) }

    function tint(rgb, a) { return Qt.rgba(rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, a) }
    function white(a) { return Qt.rgba(1, 1, 1, a) }
}
