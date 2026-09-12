#!/bin/sh
# build the chooser module: sh build.sh [<lib dir>]   (default /ujima/desktop/lib)
# needs gcc, pkg-config and libgtk-3-dev — the image chroot installs them for the build
# (os/pipeline/desktop/native.clj); on a dev Pi install them once by hand.
set -e
here=$(cd "$(dirname "$0")" && pwd)
lib=${1:-/ujima/desktop/lib}
mkdir -p "$lib"
gcc -shared -fPIC -O2 -Wall -Wextra -Wno-unused-parameter \
    $(pkg-config --cflags gtk+-3.0) -o "$lib/libujima-chooser.so" "$here/chooser.c" \
    $(pkg-config --libs gtk+-3.0)
echo "built $lib/libujima-chooser.so"
