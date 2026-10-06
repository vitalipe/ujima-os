#pragma once
#include <QAbstractNativeEventFilter>
#include <QObject>
#include <QString>
#include <QWindow>
#include <xcb/xcb.h>

// The boot cover's host: the frame set for this screen (desktop/splash/frames), once per X server (a
// marker on the root afterwards), and kept above whatever maps while it is up (it is unmanaged).
class Splash : public QObject, public QAbstractNativeEventFilter {
    Q_OBJECT
    Q_PROPERTY(bool    cover READ cover CONSTANT)
    Q_PROPERTY(QString dir   READ dir   CONSTANT)   // file URL of the frame set for this screen
    Q_PROPERTY(int     fps   READ fps   CONSTANT)
    Q_PROPERTY(int     loop  READ loop  CONSTANT)
    Q_PROPERTY(int     outro READ outro CONSTANT)
public:
    explicit Splash(const QString& framesDir, QObject* parent = nullptr);

    bool cover() const { return m_cover; }
    QString dir() const { return m_dir; }
    int fps() const { return m_fps; }
    int loop() const { return m_loop; }
    int outro() const { return m_outro; }

    void keepOnTop(QWindow* window);
    Q_INVOKABLE void done();

    bool nativeEventFilter(const QByteArray& type, void* message, qintptr* result) override;

private:
    xcb_atom_t marker() const;
    void raise() const;

    xcb_connection_t* m_c = nullptr;
    xcb_window_t m_root = XCB_NONE;
    xcb_window_t m_window = XCB_NONE;
    uint32_t m_rootMask = 0;
    bool m_ownRootEvents = false;
    bool m_cover = false;
    QString m_dir;
    int m_fps = 25, m_loop = 0, m_outro = 0;
};
