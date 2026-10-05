import QtQuick

// Attach to an item: TEXT shows in the shared tooltip card while the pointer rests on it.
HoverHandler {
    id: h
    required property var tip
    property string text
    property bool above: true
    onHoveredChanged: hovered ? tip.show(parent, text, above) : tip.hide(parent)
    onTextChanged: if (hovered && tip.visible) tip.show(parent, text, above)
}
