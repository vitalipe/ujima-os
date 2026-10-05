#pragma once
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

// systemd's notify protocol without libsystemd: one datagram to $NOTIFY_SOCKET (absent outside
// the unit, so a hand-started shell stays quiet). READY once the screens load, WATCHDOG from a
// main-thread timer — a hung main thread stops the pings and the unit kills and restarts us.
inline void sdNotify(const char* msg) {
    const char* path = getenv("NOTIFY_SOCKET");
    if (!path || !*path) return;
    sockaddr_un sa{};
    sa.sun_family = AF_UNIX;
    const size_t n = strlen(path);
    if (n >= sizeof(sa.sun_path)) return;
    memcpy(sa.sun_path, path, n);
    if (sa.sun_path[0] == '@') sa.sun_path[0] = '\0';
    const int fd = socket(AF_UNIX, SOCK_DGRAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return;
    sendto(fd, msg, strlen(msg), MSG_NOSIGNAL, reinterpret_cast<sockaddr*>(&sa),
           offsetof(sockaddr_un, sun_path) + n);
    close(fd);
}
