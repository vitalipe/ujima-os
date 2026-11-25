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
    readonly property color frostText: "#a6d3de"
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

    // one presentation per place kind; the NAME rides on the wire (place.name) — glyph, colour and copy live here
    function meta(kind, label) {
        if (kind === "session") return {
            glyph: "temp", color: "#cba878", rgb: [203, 168, 120], dashed: true,
            desc: "A scratch space for right now.", badge: "Erased at shutdown",
            notice: "Everything here is erased when the computer shuts down.",   // the design's copy sentence waits for copy/move
            emptyHint: "Anything you save now will appear here — and be erased at shutdown." }
        if (kind === "peer") return peerMeta("ro", "")
        if (kind === "usb") return {
            glyph: "usb", color: "#7e9ed6", rgb: [126, 158, 214],
            desc: "Plugged into this computer. Take it with you.",   // the label rides as a chip
            badge: "Removable", dashed: false,
            notice: "This is a USB stick. Come back to the Places screen before you unplug it.",
            emptyHint: "Use “New folder” to start organising files." }
        if (kind === "app") return {   // never on the wire: the picker's place for an app's own folder
            glyph: "folder", color: frost, rgb: frostRgb, dashed: false,
            desc: "The asking app's own folder.", badge: "This app's folder",
            notice: "", emptyHint: "Nothing here yet." }
        return {
            glyph: "local", color: "#8ab07c", rgb: [138, 176, 124],
            desc: "Shared by everyone who uses this computer.", badge: "Stays here", dashed: false,
            notice: "", emptyHint: "Use “New folder” to start organising files." }
    }

    readonly property color peer: "#b58fc9"
    readonly property var   peerRgb: [181, 143, 201]

    // a nearby computer's share: what you may do there rides on its access
    function peerMeta(access, host) {
        const rw = access === "rw"
        return {
            glyph: "peer", color: peer, rgb: peerRgb,
            noticeGlyph: rw ? "pencil" : "eye",
            notice: rw ? "Shared by " + host + ". You can open and change files here — other people may be using them too."
                       : "View only — shared by " + host + ". You can open files, but not add, rename or delete.",
            emptyHint: rw ? "Nothing here yet. Anything you add can be seen on " + host + " too."
                          : "Nothing has been shared in this folder yet." }
    }
    function accessChip(access) {
        return access === "rw" ? { text: "Can change", glyph: "pencil", tone: "frost" }
                               : { text: "View only",  glyph: "eye",    tone: "plain" }
    }

    // a nearby computer whose folders can't be opened: said once for the computer, with the cause for an admin
    function cantOpen(name, all) {
        return (all ? "The folders on " : "Some folders on ") + name + " can't be opened right now. It may be switched off or out of reach."
    }
    function forAdmin(failed) {
        const why = failed.length ? String(failed[0].reason || "").replace(/\s+/g, " ").trim() : ""
        return why !== "" ? "For an admin: " + why : ""
    }

    // token chips name the TYPE only — values never reach the wire
    function tokenMeta(type) {
        if (type === "circle/secret") return { text: "Admin key", glyph: "key" }
        if (type === "ujima/pack")    return { text: "App pack",  glyph: "pack" }
        return null
    }

    function fmtBytes(b) {
        const mb = b / 1e6
        if (mb >= 1000) { const g = mb / 1000; return (g >= 100 ? Math.round(g) : g.toFixed(1).replace(/\.0$/, "")) + " GB" }
        return Math.round(mb) + " MB"
    }
}
