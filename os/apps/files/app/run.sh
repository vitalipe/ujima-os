#!/bin/sh
# UjimaOS Files — the Qt Quick app; its tree ships with the desktop layer (/ujima/desktop/files).
set -eu
ROOT=/ujima/desktop/files
BIN="$ROOT/bin/ujima-files"
[ -x "$BIN" ] || { echo "files: no binary at $BIN (build it: cmake -S $ROOT -B /ujima/build/files && cmake --build /ujima/build/files)" >&2; exit 1; }
# the session's QT_QPA_PLATFORMTHEME=gtk3 is for Marble/Stellarium; this app paints itself
unset QT_QPA_PLATFORMTHEME
# UJIMA_FILES_ARGS: dev knobs, e.g. "--text-size Large --single-click"
exec "$BIN" --qml "$ROOT/qml" ${UJIMA_FILES_ARGS:-}
