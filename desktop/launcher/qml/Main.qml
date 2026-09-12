import QtQuick
import QtQuick.Window
import "."

// UjimaOS launcher (design turn 47a): the identity line + the category grid. JUST the launcher —
// the top bar and dock are eww's, this window fills the band i3 keeps between them. The grid is
// built ONCE from the catalog; the identity line and the open-state come live from ujimad's streams.
Window {
    id: win
    visible: true
    width: 1280; height: 684
    color: Theme.bg
    title: "Launcher"

    // what i3 reserves above and below this window for eww's bars (i3 config `gaps`)
    readonly property int barTop: 48
    readonly property int barBottom: 68

    // the SAME desktop background as the X root (wall.png), shifted up by the top bar so it lines
    // up exactly with the root behind the transparent top bar and on empty workspaces. Opaque —
    // correct text AA, and none of the see-through hover-repaint artefact the web view had.
    Image {
        source: desktop.wall
        x: 0; y: -win.barTop
        width: win.width; height: win.height + win.barTop + win.barBottom
        fillMode: Image.Stretch
        asynchronous: true
    }

    // hidden until the catalog + all its icons are painted, so a cold boot fades the finished
    // panel in instead of popping icons in one by one. The 2 s guard: a stalled icon can never
    // leave the panel stuck hidden.
    Item {
        id: home
        anchors.fill: parent
        opacity: 0
        Behavior on opacity { NumberAnimation { duration: 300 } }

        property int pending: 0          // tiles whose icon is still on its way
        function reveal() { opacity = 1 }
        function settle() { if (desktop.hasCatalog && pending === 0) reveal() }
        onPendingChanged: settle()
        Connections { target: desktop; function onAppsChanged() { Qt.callLater(home.settle) } }
        Timer { running: true; interval: 2000; onTriggered: home.reveal() }

        // the mock's small-screen steps
        readonly property bool small: win.width <= 700 || win.height <= 600

        // the catalog folded into the categories, in display order; empty ones vanish
        readonly property var cats: Theme.categories
            .map(c => Object.assign({}, c, { apps: desktop.apps.filter(a => a.category === c.key) }))
            .filter(c => c.apps.length > 0)

        // identity: the machine's display name from the settings plane, its serial tail beside it
        // (absent off-Pi — the field just stays empty)
        Row {
            id: ident
            anchors { top: parent.top; topMargin: win.height <= 600 ? 14 : 22; horizontalCenter: parent.horizontalCenter }
            spacing: 13
            Image { source: "monitor-dot.svg"; width: 30; height: 30; sourceSize: Qt.size(30, 30); anchors.verticalCenter: parent.verticalCenter }
            Text { id: machine; text: desktop.name !== "" ? desktop.name : "—"; color: Theme.text
                   font.pixelSize: home.small ? 38 : 44; font.weight: Font.Bold; font.letterSpacing: -.6; font.family: Theme.font }
            Text { text: desktop.serialTail; color: Theme.dim; anchors.baseline: machine.baseline
                   font.pixelSize: 15; font.weight: Font.DemiBold; font.letterSpacing: .5; font.family: Theme.font }
        }

        // the category grid, centred in the free space
        Item {
            anchors { top: ident.bottom; left: parent.left; right: parent.right; bottom: parent.bottom
                      topMargin: 16 + 14; bottomMargin: 16; leftMargin: 40; rightMargin: 40 }
            Grid {
                anchors.centerIn: parent
                columns: win.width <= 700 ? 1 : win.width <= 1040 ? 2 : 3
                columnSpacing: win.width <= 1040 ? 60 : 70
                rowSpacing: win.width <= 1040 ? 34 : 38
                verticalItemAlignment: Grid.AlignTop
                Repeater { model: home.cats; delegate: Category { required property var modelData; group: modelData } }
            }
        }
    }
}
