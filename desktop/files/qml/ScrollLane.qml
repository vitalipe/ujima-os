import QtQuick
import "."

// A slim vertical scrollbar for any Flickable (the grid, the places home): a hair track and a
// proportional thumb, draggable, wheel-aware, shown only while the content overflows.
Item {
    id: lane
    required property Flickable flick
    width: 8
    visible: flick.visibleArea.heightRatio < 1
    readonly property real travel: Math.max(1, height - thumb.height)          // thumb range, px
    readonly property real range: Math.max(1, flick.contentHeight - flick.height)   // content range, px

    function scrollTo(contentY) { flick.contentY = Math.max(0, Math.min(range, contentY)) }

    Rectangle { anchors.fill: parent; radius: 4; color: Qt.rgba(1, 1, 1, .05) }

    Rectangle {
        id: thumb
        width: parent.width
        radius: 4
        height: Math.max(32, lane.height * flick.visibleArea.heightRatio)
        y: lane.travel * (flick.contentY / lane.range)
        color: Qt.rgba(1, 1, 1, drag.active ? .38 : (hh.hovered && hh.point.position.y >= y && hh.point.position.y <= y + height) ? .30 : .18)
        Behavior on color { ColorAnimation { duration: 160 } }
    }

    // the hit box is wider than the drawn lane — an 8 px lane is a hard mouse target
    Item {
        id: hit
        anchors { top: parent.top; bottom: parent.bottom; right: parent.right }
        width: 24
        HoverHandler { id: hh }
        DragHandler {
            id: drag
            target: null                       // never moves the thumb itself; the binding above does
            property real startY: 0
            property bool onThumb: false
            onActiveChanged: {
                if (active) { onThumb = centroid.pressPosition.y >= thumb.y && centroid.pressPosition.y <= thumb.y + thumb.height; startY = flick.contentY }
            }
            onTranslationChanged: if (active && onThumb) lane.scrollTo(startY + translation.y * (lane.range / lane.travel))
        }
        // a tap on the track pages towards the tap
        TapHandler { onTapped: (ev) => { if (ev.position.y < thumb.y || ev.position.y > thumb.y + thumb.height) lane.scrollTo(flick.contentY + (ev.position.y < thumb.y ? -1 : 1) * flick.height * .9) } }
        WheelHandler { onWheel: (ev) => lane.scrollTo(flick.contentY - ev.angleDelta.y) }
    }
}
