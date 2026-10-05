import QtQuick

// Attach to an item: TEXT shows in the shared tooltip card while the pointer rests on it.
HoverHandler {
    id: h
    required property var tip
    property string text
    onHoveredChanged: hovered ? tip.show(parent, text, parent.mapToGlobal(point.position)) : tip.hide(parent)
    onPointChanged: if (hovered) tip.track(parent, parent.mapToGlobal(point.position))
    onTextChanged: if (hovered && tip.visible) tip.show(parent, text, tip.at)
}
