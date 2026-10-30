pragma Singleton
import QtQuick

// The app under the pointer: a launcher tile or a dock tile sets it, the launcher's band shows
// it. One value for the whole shell, since the dock is another window.
QtObject {
    property var app: null      // {id, label, category, description}
    function enter(a) { app = a }
    function leave(a) { if (app && a && app.id === a.id) app = null }
}
