# desktop/

Product source of the UjimaOS desktop, mirrored wholesale to `/ujima/desktop` by the
desktop stage (`bb dev script <ip> desktop` iterates it live).

- `shell/` — the chrome: i3 config, eww bars, icons, wallpaper.
- `launcher/` — the home surface: a Qt Quick window ujimad keeps up (host in `src/`, the
  screen in `qml/`, loaded from disk; the binary is built by the desktop stage into `bin/`).
- `files/` — the Files app and, in `--pick` mode, the portal file dialog; same shape.
- `bin/` — the desktop's programs (url handler, web-app wrappers, the portal, …); on the session PATH.
- `console/` — the chooser and the one backend behind both panels (`src/console/`),
  with the panel apps themselves at `ui/circle` and `ui/setup`.
