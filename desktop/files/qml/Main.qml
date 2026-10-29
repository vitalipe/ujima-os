import QtQuick
import QtQuick.Window
import "."

// UjimaOS Files — home = places, then one folder at a time. The shell's bars frame this
// window; the title feeds the top bar, as the mock's "Files — …" strip does.
Window {
    id: win
    visible: true
    width: 1280; height: 720
    color: Theme.bg
    title: "Files — " + (nav.place ? (nav.folderName ? nav.place.name + " / " + nav.folderName : nav.place.name) : "Places")

    QtObject {
        id: nav
        property var place: null          // a row of the places model, copied on enter
        property var path: []             // folder names below the place root
        property string selected: ""
        property string dialog: ""        // "" | mkdir | rename | delete
        property var held: null           // the one item in your hand: {path, dir, name, type, isDir, from, placeId}; outlives the place
        property string putting: ""       // where the running put lands, for the toast
        property bool menuOpen: false
        property point menuAt: Qt.point(0, 0)
        property var menuItems: []
        readonly property var meta: place ? Theme.meta(place.kind, place.label) : null
        readonly property string folderName: path.length ? path[path.length - 1] : ""
        readonly property string dir: place ? (path.length ? place.root + "/" + path.join("/") : place.root) : ""
        // the pocket shows everywhere but where the item still is; while a put runs, only where it lands
        readonly property bool pocket: !!place && (transfer.busy ? transfer.dir === dir : !!held && held.dir !== dir)

        function sync() {
            listing.hideTokenDir = !!place && place.kind === "usb" && path.length === 0
            listing.path = dir
        }
        function enter(row) { place = places.get(row); path = []; selected = ""; sync() }
        function goHome()   { place = null; path = []; selected = ""; listing.path = ""; places.refreshSpace() }
        function goRoot()   { if (path.length) { path = []; selected = ""; sync() } }
        function goBack()   { if (path.length) { path = path.slice(0, -1); selected = ""; sync() } else goHome() }
        function open(row) {
            const e = listing.get(row)
            if (e.isDir) { path = path.concat([e.name]); selected = ""; sync() }
            else { files.open(e.path); toast.show("Opening " + e.name, "open") }
        }
        function openSelected() { const i = listing.indexOf(selected); if (i >= 0) open(i) }
        function ask(kind) { if (kind === "mkdir" || selected !== "") dialog = kind }
        function confirm(value) {
            let err = ""
            if (dialog === "mkdir")       err = files.mkdir(place.root, dir, value.trim())
            else if (dialog === "rename") err = files.rename(place.root, dir, selected, value.trim())
            else if (dialog === "delete") err = files.remove(place.root, dir, selected)
            if (err !== "") { dialogBox.error = err; return }
            dialog = ""; selected = ""; listing.refresh()
        }
        function take() {
            const i = listing.indexOf(selected)
            if (i < 0 || transfer.busy) return
            const e = listing.get(i)
            held = { path: e.path, dir: dir, name: e.name, type: e.type, isDir: e.isDir, from: place.name, placeId: place.id }
            toast.show("Holding “" + e.name + "” — open the folder you want it in", "hand")
        }
        function putBack() { if (held && !transfer.busy) { toast.show("Put “" + held.name + "” back", "hand"); held = null } }
        function copyHere() { put(false) }
        function moveHere() { put(true) }
        function put(move) {
            if (!pocket || transfer.busy) return
            const err = transfer.put(place.root, dir, held.path, move, place.kind === "usb")
            if (err === "That item is gone.") held = null
            if (err !== "") { toast.show(err, "close"); return }
            putting = folderName !== "" ? folderName : place.name
        }
        function placed(error, inDir, name, moved) {
            if (error !== "") { toast.show(error, "close"); return }   // still in your hand
            const what = held ? held.name : name
            held = null
            if (dir === inDir) { listing.refresh(); selected = name; toast.show((moved ? "Moved “" : "Copied “") + what + "” here", moved ? "down" : "copy") }
            else toast.show((moved ? "Moved “" : "Copied “") + what + "” to " + putting, moved ? "down" : "copy")
        }
        function openMenu(at, onTile) {
            menuAt = at
            menuItems = onTile
                ? [{ act: "open",   label: "Open",   glyph: "open",   hint: "Enter" },
                   { act: "take",   label: "Take",   glyph: "hand",   hint: "Ctrl+C" },
                   { sep: true },
                   { act: "rename", label: "Rename", glyph: "rename", hint: "F2" },
                   { act: "delete", label: "Delete", glyph: "delete", hint: "Del", danger: true }]
                : [{ act: "mkdir",  label: "New folder", glyph: "newfolder" }]
                  .concat(pocket ? [{ sep: true },
                                    { act: "copy", label: "Copy here", glyph: "copy", hint: "Ctrl+V" },
                                    { act: "move", label: "Move here", glyph: "down" }] : [])
                  .concat(held ? [{ act: "putback", label: "Put back", glyph: "close", hint: "Esc" }] : [])
            menuOpen = true
        }
        function act(a) {
            if (a === "open") openSelected()
            else if (a === "take") take()
            else if (a === "copy") copyHere()
            else if (a === "move") moveHere()
            else if (a === "putback") putBack()
            else ask(a)
        }
    }

    // a place that departs while open (a yanked stick) drops you back home, and empties your hand
    Connections {
        target: places
        function onPlacesChanged() {
            if (nav.held && places.indexOfId(nav.held.placeId) < 0) { toast.show("“" + nav.held.name + "” went away with its place", "hand"); nav.held = null }
            if (nav.place && places.indexOfId(nav.place.id) < 0) { nav.goHome(); toast.show("That place was unplugged") }
        }
    }
    Connections { target: transfer; function onFinished(error, dir, placed, moved) { nav.placed(error, dir, placed, moved) } }

    Binding { target: listing; property: "lead"; value: nav.pocket ? 2 : 0 }

    PlacesHome { anchors.fill: parent; visible: !nav.place; onEnter: (row) => nav.enter(row) }
    PlaceView  { anchors.fill: parent; visible: !!nav.place; nav: nav }

    InPageDialog { id: dialogBox; nav: nav; onConfirm: (v) => nav.confirm(v); onCancel: nav.dialog = "" }

    Toast {
        id: toast
        function show(t, g) { text = t; glyph = g || "open"; hide.restart() }
        Timer { id: hide; interval: 2200; onTriggered: toast.text = "" }
    }

    Item {
        focus: nav.dialog === ""
        Keys.onPressed: (ev) => {
            const ctrl = ev.modifiers & Qt.ControlModifier
            if (ev.key === Qt.Key_Escape) { if (nav.menuOpen) nav.menuOpen = false; else nav.putBack(); ev.accepted = true }
            else if (ev.key === Qt.Key_Return || ev.key === Qt.Key_Enter) { nav.openSelected(); ev.accepted = true }
            else if (ev.key === Qt.Key_Backspace && nav.place) { nav.goBack(); ev.accepted = true }
            else if (ctrl && (ev.key === Qt.Key_C || ev.key === Qt.Key_X)) { nav.take(); ev.accepted = true }
            else if (ctrl && ev.key === Qt.Key_V) { nav.copyHere(); ev.accepted = true }
            else if (ev.key === Qt.Key_Delete && nav.place) { nav.ask("delete"); ev.accepted = true }
            else if (ev.key === Qt.Key_F2 && nav.place) { nav.ask("rename"); ev.accepted = true }
        }
    }
}
