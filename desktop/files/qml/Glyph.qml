import QtQuick
import "."

// A design glyph: name from the provider's table, stroke colour and width as in the mock.
Image {
    property string name: "file"
    property color color: Theme.text
    property real stroke: 1.8
    property int size: 24
    width: size; height: size
    sourceSize: Qt.size(size, size)
    source: "image://glyph/" + name + "/" + Theme.hex(color) + "/" + stroke
    smooth: true
}
