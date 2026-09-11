import QtQuick
import "."

// mkdir | rename | delete, over a dimmed surface, as designed
Rectangle {
    id: dlg
    property var nav
    property string kind: nav.dialog
    property string input: ""
    property string error: ""
    readonly property bool danger: kind === "delete"
    readonly property bool hasInput: kind === "mkdir" || kind === "rename"
    readonly property string title: kind === "mkdir" ? "New folder" : kind === "rename" ? "Rename" : "Delete this?"
    readonly property string body: kind === "mkdir" ? "Give the new folder a name."
                                 : kind === "rename" ? "Choose a new name for “" + nav.selected + "”."
                                 : "“" + nav.selected + "” will be deleted. This cannot be undone."
    readonly property string confirmLabel: kind === "mkdir" ? "Create" : kind === "rename" ? "Save" : "Delete"
    signal confirm(string value)
    signal cancel()

    anchors.fill: parent
    color: Qt.rgba(8 / 255, 10 / 255, 13 / 255, .72)
    visible: kind !== ""
    onVisibleChanged: if (visible) { input = kind === "rename" ? nav.selected : ""; error = ""; field.forceActiveFocus(); field.selectAll() }
    TapHandler { onTapped: dlg.cancel() }   // click outside = cancel

    Rectangle {
        anchors.centerIn: parent
        width: Math.min(560, parent.width - 48)
        height: head.implicitHeight + 48 + foot.height
        radius: 18
        color: Theme.panel
        border.width: 1; border.color: Qt.rgba(1, 1, 1, .06)
        TapHandler { }   // swallow

        Row {
            id: head
            anchors { left: parent.left; right: parent.right; top: parent.top; margins: 30; topMargin: 26 }
            spacing: 18
            Rectangle {
                width: 52; height: 52; radius: 15
                color: dlg.danger ? Theme.tint(Theme.dangerRgb, .14) : Theme.tint(Theme.frostRgb, .14)
                Glyph { anchors.centerIn: parent; size: 26; stroke: 1.8
                        name: dlg.kind === "mkdir" ? "newfolder" : dlg.kind === "rename" ? "rename" : "delete"
                        color: dlg.danger ? Theme.dangerText : Theme.frost }
            }
            Column {
                width: parent.width - 52 - 18
                spacing: 0
                Text { text: dlg.title; color: Theme.text; font.pixelSize: prefs.title; font.weight: Font.Bold; font.letterSpacing: -.2; font.family: Theme.font }
                Item { width: 1; height: 14 }
                Text { width: parent.width; text: dlg.body; color: Theme.dim; font.pixelSize: prefs.btn; wrapMode: Text.WordWrap; lineHeight: 1.45; font.family: Theme.font }
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
                Text { visible: dlg.error !== ""; text: dlg.error; color: Theme.dangerText; font.pixelSize: prefs.en; font.weight: Font.DemiBold; font.family: Theme.font }
            }
        }

        Rectangle {
            id: foot
            anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
            height: 86; radius: 18; color: Theme.panel2
            Rectangle { width: parent.width; height: 18; color: parent.color; anchors.top: parent.top }   // square the top corners
            Rectangle { width: parent.width; height: 1; color: Theme.hair; anchors.top: parent.top }
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
                    Text { id: okT; anchors.centerIn: parent; text: dlg.confirmLabel; color: dlg.danger ? Theme.bg : Theme.ink; font.pixelSize: prefs.btn; font.weight: Font.Bold; font.family: Theme.font }
                    HoverHandler { cursorShape: Qt.PointingHandCursor }
                    TapHandler { onTapped: dlg.confirm(dlg.input) }
                }
            }
        }
    }
}
