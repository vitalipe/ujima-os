#include "glyphs.h"
#include <QHash>
#include <QPainter>
#include <QSvgRenderer>

namespace {
const QHash<QString, QStringList>& table() {
    static const QHash<QString, QStringList> t = {
        {"folder",  {"M3 7.5A1.5 1.5 0 0 1 4.5 6h4.2a1.5 1.5 0 0 1 1.06.44l1.3 1.3a1.5 1.5 0 0 0 1.06.44H19.5A1.5 1.5 0 0 1 21 9.68V18a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18z"}},
        {"pdf",     {"M19.5 9.2V19a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.3z", "M13.8 3v4.7a1.5 1.5 0 0 0 1.5 1.5h4.2M8.6 16.6h5"}},
        {"text",    {"M5.5 3.6h13a1 1 0 0 1 1 1v14.8a1 1 0 0 1-1 1h-13a1 1 0 0 1-1-1V4.6a1 1 0 0 1 1-1z", "M8 8h8M8 11.6h8M8 15.2h5"}},
        {"image",   {"M4.5 5h15a1.5 1.5 0 0 1 1.5 1.5v11a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 17.5v-11A1.5 1.5 0 0 1 4.5 5z", "m3.4 16.4 4.8-4.8a1.5 1.5 0 0 1 2.1 0l4.3 4.3m-1.2-1.2 1.5-1.5a1.5 1.5 0 0 1 2.1 0l3.5 3.5M9.1 9.3a1.15 1.15 0 1 1-2.3 0 1.15 1.15 0 0 1 2.3 0z"}},
        {"video",   {"M3 7.7A1.7 1.7 0 0 1 4.7 6h8.6A1.7 1.7 0 0 1 15 7.7v8.6A1.7 1.7 0 0 1 13.3 18H4.7A1.7 1.7 0 0 1 3 16.3z", "m15 10.6 5.1-3a.6.6 0 0 1 .9.5v7.8a.6.6 0 0 1-.9.5l-5.1-3z"}},
        {"audio",   {"M9 17.4V6.9a1 1 0 0 1 .8-1l8.1-1.7a1 1 0 0 1 1.2 1v10.2", "M9 17.4a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0zm10.1-2.1a2.4 2.4 0 1 1-4.8 0 2.4 2.4 0 0 1 4.8 0z"}},
        {"file",    {"M19.5 9.2V19a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.3z", "M13.8 3v4.7a1.5 1.5 0 0 0 1.5 1.5h4.2"}},
        // places
        {"session", {"M12 3.4a8.6 8.6 0 1 0 0 17.2 8.6 8.6 0 0 0 0-17.2z", "M12 7.4V12l3 1.9"}},
        {"local",   {"M2.8 4.2h18.4a2 2 0 0 1 2 2v8.2a2 2 0 0 1-2 2H2.8a2 2 0 0 1-2-2V6.2a2 2 0 0 1 2-2z", "M8.4 20.2h7.2M12 16.4v3.8"}},
        {"usb",     {"M8.5 8.5V5a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1v3.5", "M6.5 8.5h11a1 1 0 0 1 1 1V19a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 19V9.5a1 1 0 0 1 1-1zM10.3 6.3h.01M13.7 6.3h.01"}},
        {"share",   {"M4.5 15.5h15a1 1 0 0 1 1 1v2a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 18.5v-2a1 1 0 0 1 1-1z", "M12 15.5V11M5.5 11h13M8.5 11V8.5a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1V11M7 18h.01"}},
        // chrome
        {"plus",    {"M12 5v14M5 12h14"}},
        {"chevron", {"M5 12h14M13 6l6 6-6 6"}},
        {"back",    {"M19 12H5M11 18l-6-6 6-6"}},
        {"crumb",   {"M9 6l6 6-6 6"}},
        {"open",    {"M14 4h6v6", "M20 4l-8.5 8.5", "M18.5 14.5V18a2 2 0 0 1-2 2h-10a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2H10"}},
        {"newfolder", {"M3 7.5A1.5 1.5 0 0 1 4.5 6h4.2a1.5 1.5 0 0 1 1.06.44l1.3 1.3a1.5 1.5 0 0 0 1.06.44H19.5A1.5 1.5 0 0 1 21 9.68V18a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18z", "M12 11.4v5M9.5 13.9h5"}},
        {"rename",  {"M12.6 6.2 4.8 14a2 2 0 0 0-.55 1.03l-.7 3.42 3.42-.7A2 2 0 0 0 8 17.2l7.8-7.8z", "m14.8 4 3.2 3.2"}},
        {"delete",  {"M4.5 6.5h15M9.5 6.5V5a1.5 1.5 0 0 1 1.5-1.5h2A1.5 1.5 0 0 1 14.5 5v1.5", "M6.5 6.5 7.3 19a1.6 1.6 0 0 0 1.6 1.5h6.2a1.6 1.6 0 0 0 1.6-1.5l.8-12.5"}},
        {"close",   {"M6 6l12 12M18 6L6 18"}},
    };
    return t;
}
}

QImage Glyphs::requestImage(const QString& id, QSize* size, const QSize& requestedSize) {
    // id = "<name>/<rrggbb>/<stroke-width>"
    const QStringList parts = id.split('/');
    const QString name  = parts.value(0);
    const QString color = "#" + parts.value(1, "e7eaef");
    const double  sw    = parts.value(2).isEmpty() ? 1.8 : parts.value(2).toDouble();

    const int px = requestedSize.width() > 0 ? requestedSize.width() : 24;
    QImage img(px, px, QImage::Format_ARGB32_Premultiplied);
    img.fill(Qt::transparent);

    const QStringList paths = table().value(name, table().value("file"));
    QString svg = QString("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%1' "
                          "stroke-width='%2' stroke-linecap='round' stroke-linejoin='round'>").arg(color).arg(sw);
    for (const QString& d : paths) svg += "<path d='" + d + "'/>";
    svg += "</svg>";

    QSvgRenderer r(svg.toUtf8());
    QPainter p(&img);
    p.setRenderHint(QPainter::Antialiasing);
    r.render(&p);
    if (size) *size = img.size();
    return img;
}
