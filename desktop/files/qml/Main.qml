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
        readonly property var meta: place ? Theme.meta(place.kind, place.label) : null
        readonly property string folderName: path.length ? path[path.length - 1] : ""
        readonly property string dir: place ? (path.length ? place.root + "/" + path.join("/") : place.root) : ""

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
            else { files.open(e.path); toast.show("Opening " + e.name) }
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
    }

    // a place that departs while open (a yanked stick) drops you back home
    Connections {
        target: places
        function onPlacesChanged() {
            if (nav.place && places.indexOfId(nav.place.id) < 0) { nav.goHome(); toast.show("That place was unplugged") }
        }
    }

    PlacesHome { anchors.fill: parent; visible: !nav.place; onEnter: (row) => nav.enter(row) }
    PlaceView  { anchors.fill: parent; visible: !!nav.place; nav: nav }

    InPageDialog { id: dialogBox; nav: nav; onConfirm: (v) => nav.confirm(v); onCancel: nav.dialog = "" }

    Toast {
        id: toast
        function show(t) { text = t; hide.restart() }
        Timer { id: hide; interval: 2200; onTriggered: toast.text = "" }
    }

    Item {
        focus: nav.dialog === ""
        Keys.onPressed: (ev) => {
            if (ev.key === Qt.Key_Return || ev.key === Qt.Key_Enter) { nav.openSelected(); ev.accepted = true }
            else if (ev.key === Qt.Key_Backspace && nav.place) { nav.goBack(); ev.accepted = true }
        }
    }
}
