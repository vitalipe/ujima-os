#!/bin/sh
# geany's dialogs fall to process cwd when no document is open (the only catalog app that does)
# — start at home, the Places screen; its terminal opens there too
cd "$HOME"
exec geany "$@"
