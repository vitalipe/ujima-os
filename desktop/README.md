# desktop/

Product source of the UjimaOS desktop, mirrored wholesale to `/ujima/desktop` by the
desktop stage (`bb dev script <ip> desktop` iterates it live).

- `shell/` — the chrome: the Qt Quick shell (host in `src/`, the screens in `qml/`, loaded from
  disk; the binary is built by the desktop stage into `bin/` and runs as the `ujima-shell` user
  unit), i3 config, picom config, eww bars, icons, wallpaper.
- `files/` — the Files app and, in `--pick` mode, the portal file dialog; same shape.
- `bin/` — the desktop's programs (url handler, web-app wrappers, the portal, …); on the session PATH.
- `console/` — the chooser and the one backend behind both panels (`src/console/`),
  with the panel apps themselves at `ui/circle` and `ui/setup`.
