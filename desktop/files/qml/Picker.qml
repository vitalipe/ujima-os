import QtQuick
import QtQuick.Window
import "."

// The portal file dialog — the web picker's UX in Qt: a rail of places, crumbs, a listing with
// Name / Size / Modified, and a footer with the type filter, the file name and Cancel / Open.
// One request drives it (the `pick` object); the answer goes back through pick.answer/cancel.
Window {
    id: win
    visible: true
    // a dialog, never a tab: a tab over the asking app gets it marked hidden by i3, and Chromium
    // aborts File System Access pickers on hidden pages. Fits between the bars (48 top, 68 dock).
    flags: Qt.Dialog
    width: Math.min(1180, Screen.width - 64); height: Math.min(720, Screen.height - 48 - 68 - 40)
    color: Theme.bg
    title: pick.title !== "" ? pick.title : (pick.directory ? "Choose a folder" : pick.mode === "save" ? "Save file" : "Open a file")
    onClosing: pick.cancel()

    readonly property color brd: Theme.brd
    readonly property color red: "#bf616a"
    readonly property string acceptText: {
        const base = pick.acceptLabel !== "" ? pick.acceptLabel : (pick.directory ? "Select" : pick.mode === "save" ? "Save" : "Open")
        return pick.multiple && nav.selected.length > 1 ? base + " (" + nav.selected.length + ")" : base
    }
    readonly property bool acceptEnabled: pick.directory ? !!nav.place
                                        : pick.mode === "save" ? nameField.text.trim() !== "" && !!nav.place
                                        : nav.selected.length > 0

    QtObject {
        id: nav
        property var place: null          // a row of the places model
        property var path: []             // folder names below the place root
        property var selected: []         // entry names in the current folder
        property int filter: -1           // index into pick.filters, -1 = all files
        property bool confirming: false
        property bool started: false
        readonly property string dir: place ? (path.length ? place.root + "/" + path.join("/") : place.root) : ""

        function sync() {
            listing.hideTokenDir = !!place && place.kind === "usb" && path.length === 0
            listing.filter = (pick.mode === "open" && !pick.directory && filter >= 0) ? pick.filters[filter].patterns : []
            listing.path = dir
        }
        function go(row, segs) { place = places.get(row); path = segs; selected = []; sync() }
        function enter(name) { path = path.concat([name]); selected = []; sync() }
        function crumb(depth) { path = path.slice(0, depth); selected = []; sync() }
        function tapped(name, isDir, toggle) {
            if (isDir && !pick.directory) { enter(name); return }
            if (isDir !== pick.directory) return                       // files are inert when picking a folder
            if (pick.multiple && toggle) selected = selected.indexOf(name) >= 0 ? selected.filter(function(n) { return n !== name }) : selected.concat([name])
            else selected = [name]
            if (pick.mode === "save" && !isDir) nameField.text = name
        }
        function doubleTapped(name, isDir) {
            if (isDir) { enter(name); return }
            if (pick.mode === "open" && !pick.directory) { selected = [name]; accept() }
        }
        function accept() {
            if (!acceptEnabled) return
            if (pick.directory) { pick.answer(place.root, [dir]); return }
            if (pick.mode === "save") {
                const name = nameField.text.trim()
                if (name === "" || name.indexOf("/") >= 0) return
                if (listing.indexOf(name) >= 0 && !listing.get(listing.indexOf(name)).isDir) { confirming = true; return }
                pick.answer(place.root, [files.join(dir, name)]); return
            }
            pick.answer(place.root, selected.map(function(n) { return files.join(dir, n) }))
        }
        function replace() { pick.answer(place.root, [files.join(dir, nameField.text.trim())]) }
        function start() {
            if (started || places.count === 0) return
            started = true
            // the folder the app asked for, if it lives in a place; else Temporary; else the first place
            for (let i = 0; i < places.count; i++) {
                const p = places.get(i)
                if (pick.currentFolder !== "" && files.inside(p.root, pick.currentFolder)) {
                    const rel = pick.currentFolder.substring(p.root.length).split("/").filter(function(s) { return s !== "" })
                    go(i, rel); return
                }
            }
            for (let i = 0; i < places.count; i++) if (places.get(i).kind === "session") { go(i, []); return }
            go(0, [])
        }
    }
    Connections {
        target: places
        function onPlacesChanged() {
            nav.start()
            if (nav.place && places.indexOfId(nav.place.id) < 0) { nav.place = null; nav.path = []; nav.selected = []; listing.path = "" }
        }
    }
    Component.onCompleted: {
        if (pick.mode === "open" && pick.filters.length) {
            let cur = -1
            for (let i = 0; i < pick.filters.length; i++) if (pick.filters[i].label === pick.currentFilter) cur = i
            nav.filter = cur >= 0 ? cur : 0
        }
        if (pick.mode === "save") { nameField.text = pick.currentName; nameField.forceActiveFocus(); nameField.selectAll() }
        nav.start()
    }

    Column {
        anchors.fill: parent

        // header
        Rectangle {
            width: parent.width; height: 58; color: "transparent"
            Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: brd }
            Row {
                anchors { left: parent.left; leftMargin: 22; verticalCenter: parent.verticalCenter }
                spacing: 12
                Glyph { name: "folder"; color: Theme.frost; size: 22; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
                Text { text: win.title; color: Theme.text; font.pixelSize: 17; font.weight: Font.DemiBold; font.letterSpacing: -.2; font.family: Theme.font; anchors.verticalCenter: parent.verticalCenter }
            }
        }

        // body: the rail of places + the listing
        Item {
            width: parent.width
            height: parent.height - 58 - 66

            Rectangle {
                id: rail
                width: 224; height: parent.height
                color: Theme.panel
                Rectangle { anchors.right: parent.right; width: 1; height: parent.height; color: brd }
                Column {
                    anchors { fill: parent; margins: 10; topMargin: 16 }
                    spacing: 4
                    Text { text: "PLACES"; color: Theme.faint; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.5; font.family: Theme.font; leftPadding: 12; bottomPadding: 8 }
                    Repeater {
                        model: places
                        delegate: Rectangle {
                            required property int index
                            required property var model
                            readonly property bool active: nav.place && nav.place.id === model.id
                            readonly property var m: Theme.meta(model.kind, model.label)
                            // colour-coded like the app's cards: the kind's tint and ring, its glyph in its colour
                            width: rail.width - 20; height: 52; radius: 12
                            color: active ? Theme.tint(m.rgb, .12) : (ph.hovered ? Qt.rgba(1, 1, 1, .05) : "transparent")
                            border.width: active ? 1 : 0; border.color: Theme.tint(m.rgb, .3)
                            Behavior on color { ColorAnimation { duration: 160 } }
                            Row {
                                anchors { left: parent.left; leftMargin: 9; verticalCenter: parent.verticalCenter }
                                spacing: 11
                                Rectangle {
                                    width: 34; height: 34; radius: 10
                                    color: Theme.tint(m.rgb, .14); border.width: 1; border.color: Theme.tint(m.rgb, .3)
                                    anchors.verticalCenter: parent.verticalCenter
                                    Glyph { anchors.centerIn: parent; name: m.glyph; color: m.color; size: 18; stroke: 1.8 }
                                }
                                Column {
                                    spacing: 1
                                    anchors.verticalCenter: parent.verticalCenter
                                    Text { text: model.name; color: Theme.text; font.pixelSize: 14; font.weight: Font.DemiBold; font.family: Theme.font }
                                    Text { text: m.badge; color: active ? m.color : Theme.faint; font.pixelSize: 11; font.weight: Font.DemiBold; font.family: Theme.font }
                                }
                            }
                            HoverHandler { id: ph; cursorShape: Qt.PointingHandCursor }
                            TapHandler { onTapped: nav.go(index, []) }
                        }
                    }
                }
            }

            Item {
                anchors { left: rail.right; right: parent.right; top: parent.top; bottom: parent.bottom }

                // crumbs
                Row {
                    id: crumbs
                    height: 44
                    anchors { left: parent.left; leftMargin: 18; verticalCenter: undefined; top: parent.top }
                    spacing: 2
                    Rectangle {
                        height: 30; width: crumb0.implicitWidth + 18 + 17; radius: 8
                        color: c0h.hovered && nav.path.length ? Qt.rgba(1, 1, 1, .05) : "transparent"
                        anchors.verticalCenter: parent.verticalCenter
                        Row {
                            anchors.centerIn: parent; spacing: 8
                            Rectangle { width: 9; height: 9; radius: 3; color: nav.place ? Theme.meta(nav.place.kind, nav.place.label).color : "transparent"; anchors.verticalCenter: parent.verticalCenter }
                            Text { id: crumb0; text: nav.place ? nav.place.name : ""; color: nav.path.length ? Theme.dim : Theme.text; font.pixelSize: 13; font.weight: Font.DemiBold; font.family: Theme.font }
                        }
                        HoverHandler { id: c0h }
                        TapHandler { onTapped: nav.crumb(0) }
                    }
                    Repeater {
                        model: nav.path
                        delegate: Row {
                            required property int index
                            required property string modelData
                            spacing: 2
                            anchors.verticalCenter: parent.verticalCenter
                            readonly property bool here: index === nav.path.length - 1
                            Glyph { name: "crumb"; color: Theme.faint; size: 13; stroke: 2; anchors.verticalCenter: parent.verticalCenter }
                            Rectangle {
                                height: 30; width: ct.implicitWidth + 18; radius: 8
                                color: ch.hovered && !here ? Qt.rgba(1, 1, 1, .05) : "transparent"
                                anchors.verticalCenter: parent.verticalCenter
                                Text { id: ct; anchors.centerIn: parent; text: modelData; color: here ? Theme.text : Theme.dim; font.pixelSize: 13; font.weight: Font.DemiBold; font.family: Theme.font }
                                HoverHandler { id: ch }
                                TapHandler { onTapped: if (!here) nav.crumb(index + 1) }
                            }
                        }
                    }
                }

                // column headers
                Item {
                    id: cols
                    anchors { left: parent.left; right: parent.right; top: crumbs.bottom }
                    height: 30
                    Rectangle { anchors.bottom: parent.bottom; width: parent.width; height: 1; color: brd }
                    Row {
                        anchors { fill: parent; leftMargin: 30; rightMargin: 26 }
                        Text { width: parent.width - 90 - 132 - 24; text: "NAME"; color: Theme.faint; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.3; font.family: Theme.font; anchors.verticalCenter: parent.verticalCenter }
                        Text { width: 90 + 12; text: "SIZE"; color: Theme.faint; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.3; font.family: Theme.font; horizontalAlignment: Text.AlignRight; anchors.verticalCenter: parent.verticalCenter }
                        Text { width: 132 + 12; text: "MODIFIED"; color: Theme.faint; font.pixelSize: 11; font.weight: Font.Bold; font.letterSpacing: 1.3; font.family: Theme.font; horizontalAlignment: Text.AlignRight; anchors.verticalCenter: parent.verticalCenter }
                    }
                }

                // rows
                ListView {
                    id: rows
                    anchors { left: parent.left; right: parent.right; top: cols.bottom; bottom: parent.bottom; leftMargin: 10; rightMargin: 10; topMargin: 8; bottomMargin: 8 }
                    clip: true
                    boundsBehavior: Flickable.StopAtBounds
                    model: listing
                    spacing: 0
                    delegate: PickerRow {
                        required property var model      // the listing's roles; PickerRow has its own name/type/…
                        width: rows.width
                        name: model.name; type: model.type; isDir: model.isDir; size: model.size; mtime: model.mtime
                        selected: nav.selected.indexOf(model.name) >= 0
                        inert: pick.directory && !model.isDir
                        onClicked: (toggle) => nav.tapped(model.name, model.isDir, toggle)
                        onDoubleClicked: nav.doubleTapped(model.name, model.isDir)
                    }
                }
                ScrollLane { flick: rows; anchors { right: parent.right; rightMargin: 4; top: rows.top; bottom: rows.bottom } }

                // empty state
                Column {
                    visible: listing.count === 0
                    anchors.centerIn: rows
                    spacing: 10
                    Glyph { name: "folder"; color: Theme.faint; size: 34; stroke: 1.5; anchors.horizontalCenter: parent.horizontalCenter }
                    Text { text: nav.place ? "Nothing here yet" : (places.connected ? "Choose a place" : "Waiting for ujimad…"); color: Theme.faint; font.pixelSize: 14; font.family: Theme.font; anchors.horizontalCenter: parent.horizontalCenter }
                }
            }
        }

        // footer: filter · name · actions — or the replace bar
        Rectangle {
            width: parent.width; height: 66
            color: nav.confirming ? Theme.panel : "transparent"
            Rectangle { anchors.top: parent.top; width: parent.width; height: 1; color: brd }

            Row {
                visible: !nav.confirming
                anchors { left: parent.left; leftMargin: 18; verticalCenter: parent.verticalCenter }
                spacing: 14
                // type filter (open mode only, as in the reference)
                Rectangle {
                    id: filterBtn
                    visible: pick.mode === "open" && !pick.directory && pick.filters.length > 0
                    height: 38; width: Math.min(230, filterText.implicitWidth + 40); radius: 10
                    color: Theme.panel; border.width: 1; border.color: brd
                    anchors.verticalCenter: parent.verticalCenter
                    Row {
                        anchors.centerIn: parent; spacing: 8
                        Text { id: filterText; text: nav.filter >= 0 ? pick.filters[nav.filter].label : "All files"; color: Theme.dim; font.pixelSize: 13; font.family: Theme.font; elide: Text.ElideRight; width: Math.min(implicitWidth, 190) }
                        Glyph { name: "crumb"; color: Theme.faint; size: 12; stroke: 2; rotation: 90; anchors.verticalCenter: parent.verticalCenter }
                    }
                    HoverHandler { cursorShape: Qt.PointingHandCursor }
                    TapHandler { onTapped: filterMenu.visible = !filterMenu.visible }
                }
                // file name (save mode)
                Rectangle {
                    visible: pick.mode === "save"
                    height: 42; width: 460; radius: 11
                    color: Theme.panel
                    border.width: nameField.activeFocus ? 2 : 1
                    border.color: nameField.activeFocus ? Theme.tint(Theme.frostRgb, .55) : brd
                    anchors.verticalCenter: parent.verticalCenter
                    TextInput {
                        id: nameField
                        anchors { fill: parent; leftMargin: 14; rightMargin: 14 }
                        verticalAlignment: TextInput.AlignVCenter
                        color: Theme.text; selectionColor: Theme.tint(Theme.frostRgb, .35)
                        font.pixelSize: 14; font.family: Theme.font
                        Keys.onReturnPressed: nav.accept()
                        Keys.onEnterPressed:  nav.accept()
                        Keys.onEscapePressed: pick.cancel()
                        Text { visible: nameField.text === ""; text: "File name"; color: Theme.faint; font: nameField.font; anchors.verticalCenter: parent.verticalCenter }
                    }
                }
            }
            Row {
                visible: !nav.confirming
                anchors { right: parent.right; rightMargin: 18; verticalCenter: parent.verticalCenter }
                spacing: 10
                PickerButton { text: "Cancel"; onClicked: pick.cancel() }
                PickerButton { text: win.acceptText; tone: "primary"; enabled: win.acceptEnabled; onClicked: nav.accept() }
            }
            // "<name> is already here — replace it?"
            Row {
                visible: nav.confirming
                anchors { left: parent.left; leftMargin: 18; verticalCenter: parent.verticalCenter }
                Text { textFormat: Text.StyledText; text: "<b>" + nameField.text.trim() + "</b> is already here — replace it?"; color: Theme.text; font.pixelSize: 14; font.family: Theme.font }
            }
            Row {
                visible: nav.confirming
                anchors { right: parent.right; rightMargin: 18; verticalCenter: parent.verticalCenter }
                spacing: 10
                PickerButton { text: "Back"; onClicked: nav.confirming = false }
                PickerButton { text: "Replace"; tone: "danger"; onClicked: nav.replace() }
            }
        }
    }

    // the filter dropdown, above the footer's filter button
    Rectangle {
        id: filterMenu
        visible: false
        x: 18; y: win.height - 66 - height - 6
        width: 230; height: menuCol.implicitHeight + 12; radius: 10
        color: Theme.panel; border.width: 1; border.color: brd
        Column {
            id: menuCol
            anchors { fill: parent; margins: 6 }
            Repeater {
                model: pick.filters.length + 1
                delegate: Rectangle {
                    required property int index
                    readonly property int fi: index < pick.filters.length ? index : -1
                    width: parent.width; height: 34; radius: 7
                    color: fi === nav.filter ? Theme.tint(Theme.frostRgb, .12) : (fh.hovered ? Qt.rgba(1, 1, 1, .05) : "transparent")
                    Text { anchors { left: parent.left; leftMargin: 10; verticalCenter: parent.verticalCenter }
                        text: fi >= 0 ? pick.filters[fi].label : "All files"; color: fi === nav.filter ? Theme.frost : Theme.text; font.pixelSize: 13; font.family: Theme.font; elide: Text.ElideRight; width: parent.width - 20 }
                    HoverHandler { id: fh; cursorShape: Qt.PointingHandCursor }
                    TapHandler { onTapped: { nav.filter = fi; nav.selected = []; nav.sync(); filterMenu.visible = false } }
                }
            }
        }
    }

    Item {
        focus: !nameField.activeFocus
        Keys.onPressed: (ev) => {
            if (ev.key === Qt.Key_Escape) { if (nav.confirming) nav.confirming = false; else if (filterMenu.visible) filterMenu.visible = false; else pick.cancel(); ev.accepted = true }
            else if ((ev.key === Qt.Key_Return || ev.key === Qt.Key_Enter) && !nav.confirming) { nav.accept(); ev.accepted = true }
        }
    }
}
