import QtQuick
import "."

// the web picker's .btn: plain · primary (frost) · danger (red)
Rectangle {
    id: btn
    property string text: ""
    property string tone: "plain"
    signal clicked()
    readonly property color red: "#bf616a"
    height: 42
    width: label.implicitWidth + 44
    radius: 12
    color: !enabled ? Theme.frost
         : tone === "primary" ? (hh.hovered ? "#9bcbd8" : Theme.frost)
         : tone === "danger" ? (hh.hovered ? "#c97781" : red)
         : (hh.hovered ? "#222834" : Theme.panel)
    opacity: enabled ? 1 : .35
    border.width: tone === "plain" ? 1 : 0
    border.color: Theme.brd
    scale: ta.pressed && enabled ? .97 : 1
    Behavior on color { ColorAnimation { duration: 120 } }
    Text { id: label; anchors.centerIn: parent; text: btn.text; color: tone === "primary" ? "#10222a" : tone === "danger" ? "#ffffff" : Theme.text; font.pixelSize: 14; font.weight: Font.DemiBold; font.family: Theme.font }
    HoverHandler { id: hh; cursorShape: btn.enabled ? Qt.PointingHandCursor : Qt.ArrowCursor }
    TapHandler { id: ta; enabled: btn.enabled; onTapped: btn.clicked() }
}
