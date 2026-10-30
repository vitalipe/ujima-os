import QtQuick
import "."

// A 52px dock tile. An action (home, a pinned app) is bare at rest and frost when its target is
// the view; a running app carries a very subtle hint of its category and blooms to the full
// category fill + ring when active. Exactly one thing is active at a time.
Rectangle {
    id: t
    required property var tip
    property string icon
    property int iconSize: 32
    property string label
    property bool active: false
    property bool app: false              // a running app, not an action
    property string category: ""
    property var entry: null              // the app it stands for, named by the launcher's band; an action has none
    signal clicked()

    readonly property var rgb: Theme.categoryRgb[category]
    readonly property var frost: [136, 192, 208]
    readonly property bool hot: hover.hovered && !active

    width: 52; height: 52; radius: 13
    color: !app ? (active ? Theme.tint(frost, .24) : hot ? Theme.white(.07) : "transparent")
         : rgb  ? Theme.tint(rgb, active ? .24 : hot ? .13 : .07)
         :        Theme.white(hot ? .10 : .05)
    border.width: active ? 1.5 : app ? 1 : 0
    border.color: !app ? Theme.tint(frost, .5)
                : rgb  ? Theme.tint(rgb, active ? .5 : hot ? .26 : .16)
                :        Theme.white(hot ? .09 : .06)
    Behavior on color { ColorAnimation { duration: 150 } }
    Behavior on border.color { ColorAnimation { duration: 150 } }

    Image {
        anchors.centerIn: parent
        width: t.iconSize; height: t.iconSize; sourceSize: Qt.size(t.iconSize, t.iconSize)
        source: t.icon
    }
    Tip { id: hover; tip: t.tip; text: t.label }
    HoverHandler { enabled: !!t.entry; onHoveredChanged: hovered ? Hover.enter(t.entry) : Hover.leave(t.entry) }
    TapHandler { onTapped: t.clicked() }
}
