#pragma once
#include <QQuickImageProvider>

// image://glyph/<name>/<rrggbb>/<stroke-width>  -> the design's 24x24 stroke glyphs,
// rasterized at the requested size with the stroke scaled like the mock's inline SVGs.
class Glyphs : public QQuickImageProvider {
public:
    Glyphs() : QQuickImageProvider(QQuickImageProvider::Image) {}
    QImage requestImage(const QString& id, QSize* size, const QSize& requestedSize) override;
};
