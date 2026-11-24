import QtQuick
import "."

// mkdir | rename | delete | share | unshare, over a dimmed surface, as designed
Rectangle {
    id: dlg
    property var nav
    property string kind: nav.dialog
    property string input: ""
    property string error: ""
    property string access: "ro"     // share: what the other computers may do
    property bool naming: false      // share: the name was refused, so ask for one
    readonly property var target: nav.shareTarget ? nav.shareTarget : ({ folder: "", name: "", access: "ro", existing: false })
    readonly property bool sharing: kind === "share" || kind === "unshare"
    readonly property bool danger: kind === "delete" || kind === "unshare"
    readonly property bool hasInput: kind === "mkdir" || kind === "rename" || (kind === "share" && naming)
    readonly property string title: kind === "mkdir" ? "New folder" : kind === "rename" ? "Rename"
                                  : kind === "share" ? (target.existing ? "Sharing settings" : "Share with computers nearby")
                                  : kind === "unshare" ? "Stop sharing?" : "Delete this?"
    readonly property string body: kind === "mkdir" ? "Give the new folder a name."
                                 : kind === "rename" ? "Choose a new name for “" + nav.selected + "”."
                                 : kind === "share" ? "Other computers nearby will see “" + target.name + "” on " + machine.name + ". What can people there do with it?"
                                 : kind === "unshare" ? "Computers nearby won't see “" + target.name + "” any more. It stays on this computer."
                                 : "“" + nav.selected + "” will be deleted. This cannot be undone." + (nav.selShared ? " It will also stop being shared." : "")
    readonly property string confirmLabel: kind === "mkdir" ? "Create" : kind === "rename" ? "Save"
                                         : kind === "share" ? (target.existing ? "Save" : "Share")
                                         : kind === "unshare" ? "Stop sharing" : "Delete"
    signal confirm(string value)
    signal cancel()
    function askName(why) { naming = true; input = target.name; error = why; field.forceActiveFocus(); field.selectAll() }

    anchors.fill: parent
    color: Qt.rgba(8 / 255, 10 / 255, 13 / 255, .72)
    visible: kind !== ""
    onVisibleChanged: if (visible) { field.forceActiveFocus(); field.selectAll() }
    onKindChanged: if (kind !== "") { input = kind === "rename" ? nav.selected : ""; error = ""; naming = false; access = target.access; field.forceActiveFocus(); field.selectAll() }
    TapHandler { onTapped: dlg.cancel() }   // click outside = cancel

    Rectangle {
        anchors.centerIn: parent
        width: Math.min(560, parent.width - 48)
        height: head.implicitHeight + 48 + foot.height
        radius: 18
        color: Theme.panel
        border.width: 1; border.color: Qt.rgba(1, 1, 1, .06)
        TapHandler { gesturePolicy: TapHandler.ReleaseWithinBounds }   // takes the grab, so the scrim never sees a tap on the card

        Row {
            id: head
            anchors { left: parent.left; right: parent.right; top: parent.top; margins: 30; topMargin: 26 }
            spacing: 18
            Rectangle {
                width: 52; height: 52; radius: 15
                color: dlg.danger ? Theme.tint(Theme.dangerRgb, .14) : Theme.tint(Theme.frostRgb, .14)
                Glyph { anchors.centerIn: parent; size: 26; stroke: 1.8
                        name: dlg.kind === "mkdir" ? "newfolder" : dlg.kind === "rename" ? "rename" : dlg.sharing ? "share" : "delete"
                        color: dlg.danger ? Theme.dangerText : Theme.frost }
            }
            Column {
                width: parent.width - 52 - 18
                spacing: 0
                Text { textFormat: Text.PlainText; text: dlg.title; color: Theme.text; font.pixelSize: prefs.title; font.weight: Font.Bold; font.letterSpacing: -.2; font.family: Theme.font }
                Item { width: 1; height: 14 }
                Text { textFormat: Text.PlainText; width: parent.width; text: dlg.body; color: Theme.dim; font.pixelSize: prefs.btn; wrapMode: Text.WordWrap; lineHeight: 1.45; font.family: Theme.font }
                Item { width: 1; height: dlg.kind === "share" ? 18 : 0 }
                Column {
                    visible: dlg.kind === "share"
                    width: parent.width
                    spacing: 10
                    Repeater {
                        model: [{ access: "ro", glyph: "eye",    title: "View only",  sub: "They can open it, but can't change anything." },
                                { access: "rw", glyph: "pencil", title: "Can change", sub: "They can also add, rename and delete files." }]
                        delegate: Rectangle {
                            id: opt
                            required property var modelData
                            readonly property bool on: dlg.access === modelData.access
                            width: parent.width; height: optText.implicitHeight + 28; radius: 13
                            color: on ? Theme.tint(Theme.frostRgb, .1) : Qt.rgba(1, 1, 1, .03)
                            border.width: on ? 2 : 1
                            border.color: on ? Theme.tint(Theme.frostRgb, .7) : Qt.rgba(1, 1, 1, .08)
                            Behavior on color { ColorAnimation { duration: 160 } }
                            Rectangle {
                                id: radio
                                anchors { left: parent.left; leftMargin: 16; verticalCenter: parent.verticalCenter }
                                width: 22; height: 22; radius: 11; color: "transparent"
                                border.width: 2; border.color: opt.on ? Theme.frost : Theme.faint
                                Rectangle { anchors.centerIn: parent; width: 10; height: 10; radius: 5; color: Theme.frost; visible: opt.on }
                            }
                            Glyph { id: optGlyph; name: opt.modelData.glyph; color: opt.on ? Theme.frost : Theme.dim; size: 22; stroke: 1.8
                                    anchors { left: radio.right; leftMargin: 14; verticalCenter: parent.verticalCenter } }
                            Column {
                                id: optText
                                anchors { left: optGlyph.right; leftMargin: 14; right: parent.right; rightMargin: 16; verticalCenter: parent.verticalCenter }
                                spacing: 2
                                Text { textFormat: Text.PlainText; text: opt.modelData.title; color: Theme.text; font.pixelSize: prefs.btn + .5; font.weight: Font.Bold; font.family: Theme.font }
                                Text { textFormat: Text.PlainText; width: parent.width; text: opt.modelData.sub; color: Theme.dim; font.pixelSize: prefs.en + 1; wrapMode: Text.WordWrap; font.family: Theme.font }
                            }
                            HoverHandler { cursorShape: Qt.PointingHandCursor }
                            TapHandler { onTapped: dlg.access = opt.modelData.access }
                        }
                    }
                }
                Item { width: 1; height: dlg.hasInput ? 16 : 0 }
                Rectangle {
                    visible: dlg.hasInput
                    width: parent.width; height: 52; radius: 11
                    color: Qt.rgba(1, 1, 1, .06)
                    border.width: field.activeFocus ? 2 : 1
                    border.color: field.activeFocus ? Theme.tint(Theme.frostRgb, .7) : Qt.rgba(1, 1, 1, .08)
                    TextInput {
                        id: field
                        anchors { fill: parent; leftMargin: 16; rightMargin: 16 }
                        verticalAlignment: TextInput.AlignVCenter
                        color: Theme.text; selectionColor: Theme.tint(Theme.frostRgb, .35)
                        font.pixelSize: prefs.btn; font.family: Theme.font
                        text: dlg.input
                        onTextChanged: { dlg.input = text; dlg.error = "" }
                        Keys.onReturnPressed: dlg.confirm(dlg.input)
                        Keys.onEnterPressed:  dlg.confirm(dlg.input)
                        Keys.onEscapePressed: dlg.cancel()
                        Text { visible: field.text === ""; text: "Name"; color: Theme.faint; font: field.font; anchors.verticalCenter: parent.verticalCenter }
                    }
                }
                Item { width: 1; height: dlg.error !== "" ? 10 : 0 }
                Text { textFormat: Text.PlainText; visible: dlg.error !== ""; width: parent.width; wrapMode: Text.WordWrap; text: dlg.error; color: Theme.dangerText; font.pixelSize: prefs.en; font.weight: Font.DemiBold; font.family: Theme.font }
            }
        }

        Rectangle {
            id: foot
            anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
            height: 86; radius: 18; color: Theme.panel2
            Rectangle { width: parent.width; height: 18; color: parent.color; anchors.top: parent.top }   // square the top corners
            Rectangle { width: parent.width; height: 1; color: Theme.hair; anchors.top: parent.top }
            Rectangle {
                visible: dlg.kind === "share" && dlg.target.existing
                anchors { left: parent.left; leftMargin: 30; verticalCenter: parent.verticalCenter }
                height: 50; width: stopT.implicitWidth + 36; radius: 11
                color: Theme.tint(Theme.dangerRgb, sh.hovered ? .12 : 0)
                Behavior on color { ColorAnimation { duration: 160 } }
                Text { id: stopT; anchors.centerIn: parent; text: "Stop sharing"; color: Theme.dangerText; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font }
                HoverHandler { id: sh; cursorShape: Qt.PointingHandCursor }
                TapHandler { onTapped: nav.dialog = "unshare" }
            }
            Row {
                anchors { right: parent.right; rightMargin: 30; verticalCenter: parent.verticalCenter }
                spacing: 11
                Rectangle {
                    height: 50; width: cancelT.implicitWidth + 44; radius: 11
                    color: Qt.rgba(1, 1, 1, ch.hovered ? .11 : .07)
                    Behavior on color { ColorAnimation { duration: 160 } }
                    Text { id: cancelT; anchors.centerIn: parent; text: "Cancel"; color: Theme.text; font.pixelSize: prefs.btn; font.weight: Font.DemiBold; font.family: Theme.font }
                    HoverHandler { id: ch; cursorShape: Qt.PointingHandCursor }
                    TapHandler { onTapped: dlg.cancel() }
                }
                Rectangle {
                    height: 50; width: okT.implicitWidth + 48; radius: 11
                    color: dlg.danger ? Theme.tint(Theme.dangerRgb, .9) : Theme.tint(Theme.frostRgb, .92)
                    Text { id: okT; textFormat: Text.PlainText; anchors.centerIn: parent; text: dlg.confirmLabel; color: dlg.danger ? Theme.bg : Theme.ink; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font }
                    HoverHandler { cursorShape: Qt.PointingHandCursor }
                    TapHandler { onTapped: dlg.confirm(dlg.input) }
                }
            }
        }
    }
}
