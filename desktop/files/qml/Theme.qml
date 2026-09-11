pragma Singleton
import QtQuick

// The design's tokens (UjimaOS Files.dc.html) and the per-kind place presentation.
QtObject {
    readonly property color bg:     "#14171b"
    readonly property color panel:  "#1b2027"
    readonly property color panel2: "#1e232b"
    readonly property color brd:    "#282e38"
    readonly property color text:   "#e7eaef"
    readonly property color dim:    "#9aa4b2"
    readonly property color faint:  "#697180"
    readonly property color hair:   Qt.rgba(1, 1, 1, .06)
    readonly property color frost:  "#88c0d0"
    readonly property color ink:    "#0d0f12"
    readonly property color dangerText: "#e2a6a6"
    readonly property color full:   "#d68383"
    readonly property var   frostRgb:  [136, 192, 208]
    readonly property var   dangerRgb: [214, 131, 131]
    readonly property var   whiteRgb:  [255, 255, 255]
    readonly property string font: "Public Sans"

    function tint(rgb, a) { return Qt.rgba(rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, a) }
    function hex(c) { return String(c).replace("#", "") }   // for image://glyph/<name>/<hex>

    readonly property var typeColor: ({
        folder: "#8ab07c", pdf: "#cba878", image: "#7e9ed6", video: "#b58fc9",
        audio: "#cf90a8", text: "#9aa4b2", file: "#9aa4b2" })
    function colorOf(type) { return typeColor[type] || typeColor.file }

    // one presentation per place kind; the stream carries kind + label + root
    function meta(kind, label) {
        if (kind === "usb") return {
            name: "USB Stick", glyph: "usb", color: "#7e9ed6", rgb: [126, 158, 214],
            desc: label ? "“" + label + "” — plugged into this computer. Take it with you."
                        : "Plugged into this computer. Take it with you.",
            badge: "Removable",
            notice: "This is a USB stick. Go back to this screen before you unplug it." }
        return {
            name: "This Computer", glyph: "local", color: "#8ab07c", rgb: [138, 176, 124],
            desc: "Shared by everyone who uses this computer.", badge: "Stays here", notice: "" }
    }

    function fmtBytes(b) {
        const mb = b / 1e6
        if (mb >= 1000) { const g = mb / 1000; return (g >= 100 ? Math.round(g) : g.toFixed(1).replace(/\.0$/, "")) + " GB" }
        return Math.round(mb) + " MB"
    }
}
