import QtQuick
import QtQuick.Window
import "."

// The boot cover: maps over the still mark X keeps (same frames), fades the glint back in, loops
// until the desktop under it is ready, then plays the outro and fades out to it.
Window {
    id: win
    property bool ready: false            // the desktop underneath is up: play the outro
    signal finished()

    flags: Qt.FramelessWindowHint | Qt.X11BypassWindowManagerHint
    x: 0; y: 0
    width: Screen.width; height: Screen.height
    color: "black"
    title: "Cover"
    visible: true

    readonly property real fadeIn: 0.3    // as long as the splash took to fade the glint out
    property int tick: 0                  // frames since the cover mapped
    property int outroAt: -1              // the tick the outro started on

    function frame(name, n) { return splash.dir + "/" + name + "-" + String(n).padStart(4, "0") + ".png" }
    function clamp01(x) { return Math.max(0, Math.min(1, x)) }
    function easeInOutCubic(x) { return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2 }

    // the frames are pixel-for-pixel the splash's, centred like Plymouth centres them
    component Frame: Image {
        anchors.centerIn: parent
        width: sourceSize.width / Screen.devicePixelRatio
        height: sourceSize.height / Screen.devicePixelRatio
        cache: false
    }
    // rest sits under everything: frames are opaque, so it only shows while the top one fades in
    Frame { id: rest; source: splash.dir + "/rest.png" }
    Frame { id: top; opacity: 0 }
    // the last loop frame, fading over the outro's first frames — the glint stops wherever it was
    Frame { id: trail; opacity: 0 }

    Timer {
        interval: 1000 / splash.fps
        repeat: true
        running: win.visible
        onTriggered: win.step()
    }
    // a stalled tier can never leave the screen covered
    Timer { running: true; interval: 20000; onTriggered: win.ready = true }

    function step() {
        const t = tick / splash.fps
        if (outroAt < 0) {
            top.source = frame("loop", tick % splash.loop)
            top.opacity = clamp01(t / fadeIn)
            if (ready && t >= fadeIn) {
                trail.source = top.source
                trail.opacity = 1
                outroAt = tick
            }
        }
        if (outroAt >= 0) {
            const n = tick - outroAt
            if (n >= splash.outro) { visible = false; finished(); return }
            const ot = n / splash.fps
            top.source = frame("outro", n)
            trail.opacity = clamp01(1 - ot / 0.25)
            // the design's mark fade (outro 0.55 → 1.15 s) takes the black with it: the desktop
            // comes up through the fading mark
            win.opacity = 1 - easeInOutCubic(clamp01((ot - 0.55) / 0.6))
        }
        tick++
    }
}
