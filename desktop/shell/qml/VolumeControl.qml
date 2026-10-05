import QtQuick
import "."

// Volume, inline: hovering the speaker slides a slider + its number out to the LEFT — leftwards
// is load-bearing, the tray is right-anchored, so the speaker stays under the pointer. The speaker
// and the number both mute. The slider holds its own value while dragged and posts at most one
// change per 250 ms, the last on release. Pinned by an activity: a padlock; a pinned level
// greys its track and loses its knob's drag, and muted + a pinned mute shows no slider at all.
Item {
    id: v
    required property var tip
    readonly property var a: desktop.audio
    readonly property var level: a.volume ? a.volume.value : null          // null = no output
    readonly property bool muted: !!(a.muted && a.muted.value)
    readonly property bool mutedLocked: !!(a.muted && a.muted.locked)
    readonly property bool volumeLocked: !!(a.volume && a.volume.locked)
    readonly property bool noOutput: level === null || level === undefined
    readonly property bool open: hover.hovered && !(muted && mutedLocked)
    readonly property int margin: open ? 4 : 0

    property int dragged: -1                       // the knob while a finger holds it; -1 = follow ujimad
    readonly property int shown: dragged >= 0 ? dragged : (noOutput ? 0 : level)

    readonly property string muteTip: mutedLocked ? "Sound is locked"
                                    : noOutput    ? "No audio output"
                                    : muted       ? "Unmute" : "Mute"
    function toggleMute() { if (!mutedLocked) desktop.setMuted(!muted) }

    implicitWidth: margin + box.width
    implicitHeight: 32

    Item {
        id: box
        x: v.margin
        width: reveal.width + 4 + sound.width; height: 32
        HoverHandler { id: hover; onHoveredChanged: if (!hovered) v.dragged = -1 }

        // open, the slider + number + speaker are one control: a shallow well says so
        Rectangle {
            anchors.fill: parent; radius: height / 2
            color: v.open ? Qt.rgba(0, 0, 0, .22) : "transparent"
            Behavior on color { ColorAnimation { duration: 160 } }
        }

        Item {
            id: reveal
            width: v.open ? 8 + 130 + 8 + 28 : 0; height: 32; clip: true
            Behavior on width { NumberAnimation { duration: 180; easing.type: Easing.OutCubic } }

            Item {
                anchors.right: parent.right
                width: 8 + 130 + 8 + 28; height: 32

                // the scale: a 5px trough, the knob's 13px face on a 5px travel box (GTK's)
                Item {
                    id: scale
                    x: 8; width: 130; height: 32
                    opacity: v.noOutput ? .35 : 1
                    readonly property real centre: 2.5 + (width - 5) * v.shown / 100
                    readonly property color fill: v.muted || v.volumeLocked ? Theme.faint : "#7e9ed6"
                    Rectangle {
                        y: 16 - 2.5; width: parent.width; height: 5; radius: 2.5
                        color: Theme.white(.17)
                    }
                    Rectangle {
                        y: 16 - 2.5; width: scale.centre; height: 5; radius: 2.5
                        color: scale.fill
                    }
                    Rectangle {
                        x: scale.centre - 6.5; y: 16 - 6.5; width: 13; height: 13; radius: 6.5
                        color: v.volumeLocked ? Theme.dim : "#9db6e0"
                    }
                    MouseArea {
                        anchors.fill: parent
                        enabled: !v.volumeLocked
                        function at(x) { return Math.round(Math.max(0, Math.min(1, (x - 2.5) / (width - 5))) * 100) }
                        onPressed: m => { v.dragged = at(m.x); throttle.send() }
                        onPositionChanged: m => { if (pressed) { v.dragged = at(m.x); throttle.later() } }
                        onReleased: {
                            throttle.flush()
                            // unmute when the DRAG ENDS: a machine the user muted stays silent while adjusted
                            if (v.muted && !v.mutedLocked) desktop.setMuted(false)
                        }
                    }
                    Timer {
                        id: throttle
                        interval: 250
                        property int sent: -1
                        function send() { if (v.dragged >= 0 && v.dragged !== sent) { sent = v.dragged; desktop.setVolume(sent) } restart() }
                        function later() { if (!running) send() }
                        function flush() { stop(); send(); stop() }
                        onTriggered: if (v.dragged !== sent) send()
                    }
                }

                // the number mutes too
                Text {
                    x: 8 + 130 + 8; width: 28; anchors.verticalCenter: parent.verticalCenter
                    horizontalAlignment: Text.AlignRight
                    text: v.noOutput ? "--" : v.shown
                    color: numHover.hovered ? Theme.text : Theme.dim
                    Behavior on color { ColorAnimation { duration: 150 } }
                    font { family: Theme.font; pixelSize: 14; weight: Font.Medium }
                    Tip { id: numHover; tip: v.tip; text: v.muteTip }
                    TapHandler { onTapped: v.toggleMute() }
                }
            }
        }

        // the speaker keeps its whole ladder; a pinned level or mute adds the padlock at its corner
        Item {
            id: sound
            x: reveal.width + 4
            readonly property bool lockShown: v.mutedLocked || v.volumeLocked
            width: 8 + (lockShown ? 9 : 0) + 20 + 8; height: 32
            Image {
                x: 8; y: 8; width: 10; height: 10; sourceSize: Qt.size(10, 10)
                source: Theme.icon("lockmark.svg")
                visible: sound.lockShown
            }
            Image {
                x: 8 + (sound.lockShown ? 9 : 0); y: 6
                width: 20; height: 20; sourceSize: Qt.size(20, 20)
                source: Theme.icon(v.muted               ? "speaker-muted.svg"
                                       : v.noOutput || v.level === 0 ? "speaker-off.svg"
                                       : v.level <= 50         ? "speaker-low.svg"
                                       :                         "speaker.svg")
            }
            Tip { tip: v.tip; text: v.muteTip }
            TapHandler { onTapped: v.toggleMute() }
        }
    }
}
