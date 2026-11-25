import QtQuick
import QtQuick.Window
import "."

// UjimaOS Files — home = places, then one folder at a time; a nearby computer is a level of its own
// between home and the folders it shares. The shell's bars frame this window; the title feeds the top
// bar, as the mock's "Files — …" strip does.
Window {
    id: win
    visible: true
    width: 1280; height: 720
    color: Theme.bg
    title: "Files — " + (nav.place ? (nav.place.kind === "peer" ? nav.place.host + " / " + nav.place.label : nav.place.name) + (nav.folderName ? " / " + nav.folderName : "")
                                   : nav.host ? nav.host.name : home.tabLabel)
    // closing in the middle of a copy asks first; the answer stops the copy and closes when it has cleaned up
    onClosing: (close) => {
        if (!transfer.busy) return
        close.accepted = false
        if (!nav.quitting) { ++nav.op; nav.working = false; nav.dialog = "quit" }
    }

    QtObject {
        id: nav
        property var place: null          // a row of the places model or a nearby share, copied on enter
        property string computer: ""      // the nearby computer whose folders are listed, by machine id; a folder opened from that list keeps it, so Back lands there
        property string computerName: ""
        readonly property var host: computer === "" ? null : (places.nearby.find((c) => c.id === computer) || null)
        property string selShare: ""      // the folder picked on that list: its place id
        property var hushed: ({})         // computers whose can't-open message was put away, by machine id
        property string retrying: ""      // the computer asked for its folders again, until the stream says how it went
        property bool retryStale: false   // its :invalid is the old outcome until it mounts again
        property string opening: ""       // the nearby share asked for: its place id, until the stream says how it went
        property bool openingStale: false // asked of a share that had failed: its :invalid is the old outcome until it mounts again
        readonly property bool waiting: opening !== "" && !place   // asked from a list: the window is held until it is in, or let go
        property var path: []             // folder names below the place root
        property string selected: ""
        property string dialog: ""        // "" | mkdir | rename | delete | share | unshare | quit
        property bool working: false      // the dialog's answer is being carried out: it stays, and says so after a second
        property int op: 0                // which answer that is; one that lands after its dialog was let go is dropped
        property bool quitting: false     // the window closes when the copy has stopped
        property var shareTarget: null    // the folder the share dialogs are about: {kind, wire, path, folder, name, access, existing}
        property var held: null           // the one item in your hand: {path, dir, name, type, isDir, from, placeId, kind, wire, rel}; outlives the place
        property string putting: ""       // where the running put lands, for the toast
        property string puttingName: ""   // and what it is
        property var putTo: null          // and as a place: {kind, wire, rel}
        property bool menuOpen: false
        property point menuAt: Qt.point(0, 0)
        property var menuItems: []
        readonly property var meta: !place ? null : place.kind === "peer" ? Theme.peerMeta(place.access, place.host) : Theme.meta(place.kind, place.label)
        readonly property bool canWrite: !place || place.kind !== "peer" || place.access === "rw"
        readonly property bool canShare: !!place && place.kind !== "peer"
        // count is read so this follows the rows as they land, not only the selection
        readonly property bool selDir: listing.count > 0 && selected !== "" && !!listing.get(listing.indexOf(selected)).isDir
        readonly property bool selShared: !!place && selected !== "" && sharesUnder(place.id, rel(selected)).length > 0
        // this place's shares, by path
        readonly property var shares: {
            const out = {}
            if (place) for (const s of places.shared) if (s.place === place.id) out[s.path] = s
            return out
        }
        function rel(name) { return path.concat([name]).join("/") }
        readonly property string folderName: path.length ? path[path.length - 1] : ""
        readonly property string dir: place ? (path.length ? place.root + "/" + path.join("/") : place.root) : ""
        // the pocket shows everywhere but where the item still is; while a put runs, only where it lands
        readonly property bool pocket: !!place && (transfer.busy ? transfer.dir === dir : !!held && held.dir !== dir && canWrite)

        function sync() {
            listing.hideTokenDir = !!place && place.kind === "usb" && path.length === 0
            listing.watch = !place || place.kind !== "peer"
            listing.path = dir
        }
        function enterPlace(p) { place = p; path = []; selected = ""; sync() }
        function openComputer(c) { computer = c.id; computerName = c.name; selShare = "" }
        // the trail's computer: its list, however this folder was reached
        function toComputer() {
            if (!place || place.kind !== "peer") return
            computer = place.hostId; computerName = place.host; selShare = place.id
            leave()
        }
        // folders of one access ride one connection, so asking for one of each asks for all that failed
        function retry(c) {
            retrying = c.id; retryStale = true; giveUp.restart()
            const asked = {}
            for (const s of c.shares) {
                if (s.state !== "invalid" || asked[s.access]) continue
                asked[s.access] = true
                commands.post("places/mount", { place: ["peer", s.wire] }, (status) => {
                    if ((status < 200 || status >= 300) && retrying === c.id) { retrying = ""; toast.show("Couldn't ask " + c.name + " again", "warn") }
                })
            }
        }
        function retried() {
            const c = places.nearby.find((h) => h.id === retrying)
            if (c && c.shares.some((s) => s.state === "mounting")) retryStale = false
            else if (!c || !retryStale || !c.shares.some((s) => s.state === "invalid")) retrying = ""
        }
        function hush(id) { const h = Object.assign({}, hushed); h[id] = true; hushed = h }
        // a message put away stays away only while the failure it was about stands
        function unhush() {
            const keep = {}
            for (const c of places.nearby) if (hushed[c.id] && c.shares.some((s) => s.state === "invalid")) keep[c.id] = true
            if (Object.keys(keep).length !== Object.keys(hushed).length) hushed = keep
        }
        function enter(row) { enterPlace(places.get(row)) }
        // a share's root is mounted with its computer's other shares of the same access, so the stream
        // cannot say which one was asked for: `opening` remembers
        function openShare(share) {
            if (share.state === "ready") { opening = ""; enterPlace(share); return }
            opening = share.id
            openingStale = share.state === "invalid"
            if (share.state !== "mounting")
                commands.post("places/mount", { place: ["peer", share.wire] }, (status) => {
                    if ((status < 200 || status >= 300) && opening === share.id) { opening = ""; toast.show("That folder can't be opened right now", "warn") }
                })
        }
        function opened() {
            const share = places.peer(opening)
            if (!share.id) { opening = ""; if (place) goHome(); toast.show("That folder isn't shared any more", "warn") }
            else if (share.state === "ready") { opening = ""; if (!place) enterPlace(share) }
            else if (share.state === "mounting") openingStale = false
            else if (share.state === "invalid" && !openingStale) { opening = ""; if (place) goHome(); toast.show("Couldn't open “" + share.label + "” on " + share.host, "warn") }
        }
        // the open share follows what its computer says of it now. A change of access is the same folder
        // under that computer's other root, opened if it is not yet
        function follow() {
            const now = places.peer(place.id)
            if (!now.id || now.access === place.access && now.host === place.host) return
            const changed = now.access !== place.access
            if (now.state === "ready") {
                opening = ""; place = now; sync()
                if (changed) toast.show(now.access === "rw" ? "“" + now.label + "” can now be changed" : "“" + now.label + "” is now view only", now.access === "rw" ? "pencil" : "eye")
            } else if (changed && opening !== now.id) openShare(now)
        }
        // what is in your hand from a share is found under the root that share has now
        function rehold() {
            const now = places.peer(held.placeId)
            if (now.state !== "ready" || held.path === now.root + "/" + held.rel) return
            const parent = held.rel.split("/").slice(0, -1).join("/")
            held = Object.assign({}, held, { path: now.root + "/" + held.rel, dir: parent !== "" ? now.root + "/" + parent : now.root, from: now.name })
        }
        function leave() {
            place = null; path = []; selected = ""; listing.path = ""; places.refreshSpace()
            if (dialog !== "quit") { ++op; working = false; dialog = "" }
        }
        function goHome()   { computer = ""; selShare = ""; leave() }
        function goRoot()   { if (path.length) { path = []; selected = ""; sync() } }
        // the way in, backwards: a folder, then the place, then the computer's list if that is where it was opened
        function goBack() {
            if (path.length) { path = path.slice(0, -1); selected = ""; sync() }
            else if (place && host) leave()
            else goHome()
        }
        function open(row) {
            const e = listing.get(row)
            if (e.isDir) { path = path.concat([e.name]); selected = ""; sync() }
            else { files.open(e.path); toast.show("Opening " + e.name, "open") }
        }
        function openSelected() {
            if (!place) { const s = host ? places.peer(selShare) : null; if (s && s.id && s.hostId === computer) openShare(s); return }
            const i = listing.indexOf(selected); if (i >= 0) open(i)
        }
        function ask(kind) { if (canWrite && (kind === "mkdir" || selected !== "")) dialog = kind }
        function confirm(value) {
            if (working) return
            if (dialog === "quit")    { quitting = true; working = true; transfer.cancel(); return }
            if (dialog === "share")   { share(dialogBox.naming ? value.trim() : ""); return }
            if (dialog === "unshare") { unshare(); return }
            const kind = dialog, at = place, name = selected, next = value.trim(), id = ++op
            const mine = kind === "mkdir" ? [] : sharesUnder(at.id, rel(name))
            const from = rel(name), to = rel(next)
            const then = (err) => {
                if (id !== op) return
                working = false
                if (err !== "") { dialogBox.error = err; return }
                if (kind === "rename")      reshare(mine, from, to, at, name, next)
                else if (kind === "delete") for (const s of mine) commands.post("shares/remove", { place: [s.kind, s.wire], path: s.path })
                dialog = ""; selected = ""; listing.refresh()
            }
            working = true; dialogBox.error = ""
            if (kind === "mkdir")       files.mkdir(at.root, dir, next, then)
            else if (kind === "rename") files.rename(at.root, dir, name, next, then)
            else                        files.remove(at.root, dir, name, then)
        }

        // --- sharing: the list is ujimad's; every change is a verb, and the places stream brings it back
        function askShare() {
            if (!canShare || !selDir) return
            const s = shares[rel(selected)]
            shareTarget = { kind: place.kind, wire: place.wire, path: rel(selected), folder: selected,
                            name: s ? s.name : selected, access: s ? s.access : "ro", existing: !!s }
            dialog = "share"
        }
        function askShared(s, kind) {   // from the Shared from here tab: share | unshare
            shareTarget = { kind: s.kind, wire: s.wire, path: s.path, folder: s.name, name: s.name, access: s.access, existing: true }
            dialog = kind
        }
        function share(name) {
            const t = shareTarget
            const params = { place: [t.kind, t.wire], path: t.path, access: dialogBox.access }
            if (name !== "") params.name = name
            commands.post("shares/add", params, (status) => {
                if (dialog !== "share") return
                if (status >= 200 && status < 300) { dialog = ""; toast.show(t.existing ? "Sharing updated" : "“" + (name || t.name) + "” is now shared with computers nearby", "share") }
                else if (status === 409) dialogBox.askName("Another shared folder already has that name. Give this one a name of its own.")
                else if (status === 400) dialogBox.askName("That name can't be used. Try a shorter one, without marks like “/” or “:”.")
                else dialogBox.error = "Couldn't share it. Try again."
            })
        }
        function unshare() {
            const t = shareTarget
            commands.post("shares/remove", { place: [t.kind, t.wire], path: t.path }, (status) => {
                if (dialog !== "unshare") return
                if (status >= 200 && status < 300 || status === 404) { dialog = ""; toast.show("Stopped sharing “" + t.name + "”", "share") }
                else dialogBox.error = "Couldn't stop sharing it. Try again."
            })
        }
        function sharesUnder(placeId, at) {
            return places.shared.filter((s) => s.place === placeId && (s.path === at || s.path.startsWith(at + "/")))
        }
        // a folder renamed or moved takes its shares along: each leaves the old path, then joins at the new one.
        // A share named after the folder follows its new name, unless another share has it or it can't be a share's name
        function reshare(list, from, to, dest, oldName, newName) {
            for (const s of list) {
                const name = s.path === from && s.name === oldName ? newName : s.name
                const add = (n, then) => commands.post("shares/add", { place: [dest.kind, dest.wire], path: to + s.path.slice(from.length), access: s.access, name: n }, then)
                commands.post("shares/remove", { place: [s.kind, s.wire], path: s.path },
                              () => add(name, (status) => { if ((status === 409 || status === 400) && name !== s.name) add(s.name) }))
            }
        }

        function take() {
            const i = listing.indexOf(selected)
            if (i < 0 || transfer.busy) return
            const e = listing.get(i)
            held = { path: e.path, dir: dir, name: e.name, type: e.type, isDir: e.isDir, from: place.name, placeId: place.id,
                     kind: place.kind, wire: place.wire, rel: rel(e.name) }
            toast.show("Holding “" + e.name + "” — open the folder you want it in", "hand")
        }
        function putBack() { if (held && !transfer.busy) { toast.show("Put “" + held.name + "” back", "hand"); held = null } }
        function copyHere() { put(false) }
        function moveHere() { put(true) }
        function put(move) {
            if (!pocket || transfer.busy) return
            const err = transfer.put(place.root, dir, held.path, move, place.kind === "usb")
            if (err !== "") { toast.show(err, "close"); return }
            putting = folderName !== "" ? folderName : place.name
            puttingName = held.name
            putTo = { kind: place.kind, wire: place.wire, rel: path.join("/") }
        }
        function placed(error, inDir, name, moved, cancelled) {
            if (cancelled) { toast.show(moved ? "Move cancelled" : "Copy cancelled", "close"); return }
            if (error === "That item is gone.") held = null
            if (error !== "") { toast.show(error, "close"); return }   // still in your hand
            const what = held ? held.name : name
            if (moved && held && held.isDir && putTo) {
                const mine = sharesUnder(held.placeId, held.rel)
                if (putTo.kind === "peer") for (const s of mine) commands.post("shares/remove", { place: [s.kind, s.wire], path: s.path })
                else reshare(mine, held.rel, (putTo.rel !== "" ? putTo.rel + "/" : "") + name, putTo, held.name, name)
            }
            held = null
            if (dir === inDir) { listing.refresh(); selected = name; toast.show((moved ? "Moved “" : "Copied “") + what + "” here", moved ? "down" : "copy") }
            else toast.show((moved ? "Moved “" : "Copied “") + what + "” to " + putting, moved ? "down" : "copy")
        }
        function openMenu(at, onTile) {
            menuAt = at
            menuItems = onTile
                ? [{ act: "open",   label: "Open",   glyph: "open",   hint: "Enter" },
                   { act: "take",   label: "Take",   glyph: "hand",   hint: "Ctrl+C" }]
                  .concat(canShare && selDir ? [{ act: "share", label: "Share", glyph: "share" }] : [])
                  .concat([
                   { sep: true },
                   { act: "rename", label: "Rename", glyph: "rename", hint: "F2", enabled: canWrite },
                   { act: "delete", label: "Delete", glyph: "delete", hint: "Del", danger: true, enabled: canWrite }])
                : [{ act: "mkdir",  label: "New folder", glyph: "newfolder", enabled: canWrite }]
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
            else if (a === "share") askShare()
            else ask(a)
        }
    }

    // a place that departs while open (a yanked stick, a computer gone quiet) drops you back home, and empties your hand
    Connections {
        target: places
        function onPlacesChanged() {
            if (nav.place && nav.place.kind === "peer") nav.follow()
            if (nav.held && nav.held.kind === "peer") nav.rehold()
            if (nav.held && !places.hasReady(nav.held.placeId) && nav.opening !== nav.held.placeId) { toast.show("“" + nav.held.name + "” went away with its place", "hand"); nav.held = null }
            if (nav.place && !places.hasReady(nav.place.id) && nav.opening !== nav.place.id) {
                const peer = nav.place.kind === "peer"
                nav.goHome()
                toast.show(peer ? "That shared folder went away, so we brought you back" : "That place was unplugged", peer ? "warn" : "open")
            }
            if (nav.opening !== "") nav.opened()
            if (nav.retrying !== "") nav.retried()
            nav.unhush()
            if (nav.host) nav.computerName = nav.host.name
            else if (nav.computer !== "" && !nav.place) {
                const name = nav.computerName
                nav.goHome()
                toast.show(name + " isn't sharing anything now, so we brought you back", "warn")
            }
        }
    }
    Timer { id: giveUp; interval: 60000; onTriggered: nav.retrying = "" }
    Connections {
        target: transfer
        function onFinished(error, dir, placed, moved, cancelled) {
            if (nav.quitting) { Qt.quit(); return }
            if (nav.dialog === "quit") nav.dialog = ""
            nav.placed(error, dir, placed, moved, cancelled)
        }
    }

    Binding { target: listing; property: "lead"; value: nav.pocket ? 2 : 0 }

    TransferBar { id: bar; what: nav.puttingName; where: nav.putting }

    PlacesHome { id: home; anchors { fill: parent; topMargin: bar.height } visible: !nav.place && !nav.host
                 opening: nav.opening; hushed: nav.hushed; retrying: nav.retrying
                 onEnter: (row) => nav.enter(row); onOpenShare: (share) => nav.openShare(share)
                 onOpenComputer: (host) => nav.openComputer(host); onRetry: (host) => nav.retry(host); onHush: (id) => nav.hush(id)
                 onChangeShare: (share) => nav.askShared(share, "share"); onStopShare: (share) => nav.askShared(share, "unshare") }
    ComputerView { anchors { fill: parent; topMargin: bar.height } visible: !nav.place && !!nav.host; nav: nav }
    PlaceView  { anchors { fill: parent; topMargin: bar.height } visible: !!nav.place; nav: nav }

    // letting go only stops the waiting: the folder goes on opening, and is in at once when asked again
    OpeningBox { on: nav.waiting; share: on ? places.peer(nav.opening) : ({}); onCancel: nav.opening = "" }

    InPageDialog { id: dialogBox; nav: nav; onConfirm: (v) => nav.confirm(v); onCancel: if (!nav.working) nav.dialog = "" }

    Toast {
        id: toast
        function show(t, g) { text = t; glyph = g || "open"; hide.restart() }
        Timer { id: hide; interval: 2200; onTriggered: toast.text = "" }
    }

    Item {
        focus: nav.dialog === ""
        Keys.onPressed: (ev) => {
            const ctrl = ev.modifiers & Qt.ControlModifier
            if (nav.waiting) { if (ev.key === Qt.Key_Escape) nav.opening = ""; ev.accepted = true; return }
            if (ev.key === Qt.Key_Escape) { if (nav.menuOpen) nav.menuOpen = false; else nav.putBack(); ev.accepted = true }
            else if (ev.key === Qt.Key_Return || ev.key === Qt.Key_Enter) { nav.openSelected(); ev.accepted = true }
            else if (ev.key === Qt.Key_Backspace && (nav.place || nav.host)) { nav.goBack(); ev.accepted = true }
            else if (ctrl && (ev.key === Qt.Key_C || ev.key === Qt.Key_X)) { nav.take(); ev.accepted = true }
            else if (ctrl && ev.key === Qt.Key_V) { nav.copyHere(); ev.accepted = true }
            else if (ev.key === Qt.Key_Delete && nav.place) { nav.ask("delete"); ev.accepted = true }
            else if (ev.key === Qt.Key_F2 && nav.place) { nav.ask("rename"); ev.accepted = true }
        }
    }
}
