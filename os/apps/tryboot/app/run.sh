#!/bin/sh
# The trial-boot page: the console backend on a port of its own, so it sits beside the Console.
set -eu

PORT=1339
ROOT=/ujima/desktop/console

[ -d "$ROOT/src" ] || { echo "console not installed at $ROOT" >&2; exit 1; }

cd "$ROOT"
UJIMA_CONSOLE_PORT=$PORT bb -cp "src:/ujima/runtime/src" -m console.main &

until curl -sf "http://127.0.0.1:$PORT/" >/dev/null 2>&1; do sleep 0.2; done

# as the Console: only the backend keeps the scope's privilege.
exec setpriv --no-new-privs ujima-open-web-app "http://127.0.0.1:$PORT/trial-boot/" ujima-tryboot
