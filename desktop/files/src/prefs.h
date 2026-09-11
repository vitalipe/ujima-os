#pragma once
#include <QObject>

// The design's text-size scale (the --fs-* vars) and the interaction switch, from argv.
class Prefs : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString textSize READ textSize CONSTANT)
    Q_PROPERTY(bool singleClickOpen READ singleClickOpen CONSTANT)
    Q_PROPERTY(double name  READ name  CONSTANT)
    Q_PROPERTY(double en    READ en    CONSTANT)
    Q_PROPERTY(double btn   READ btn   CONSTANT)
    Q_PROPERTY(double h     READ h     CONSTANT)
    Q_PROPERTY(double title READ title CONSTANT)
    Q_PROPERTY(double sub   READ sub   CONSTANT)
    Q_PROPERTY(double card  READ card  CONSTANT)
    Q_PROPERTY(double badge READ badge CONSTANT)
public:
    Prefs(const QString& textSize, bool singleClick, QObject* parent = nullptr)
        : QObject(parent), m_textSize(textSize), m_single(singleClick) {
        // Normal / Large / Extra large, as in the mock's SCALES table
        if (textSize == "Large")            m_s = {17.5, 13, 16,   22, 26, 16,   24, 13};
        else if (textSize == "Extra large") m_s = {19.5, 14, 17.5, 24, 29, 17.5, 27, 14};
        else                                m_s = {15.5, 12, 14.5, 20, 23, 15,   22, 12};
    }
    QString textSize() const { return m_textSize; }
    bool singleClickOpen() const { return m_single; }
    double name()  const { return m_s.name; }
    double en()    const { return m_s.en; }
    double btn()   const { return m_s.btn; }
    double h()     const { return m_s.h; }
    double title() const { return m_s.title; }
    double sub()   const { return m_s.sub; }
    double card()  const { return m_s.card; }
    double badge() const { return m_s.badge; }
private:
    struct Scale { double name, en, btn, h, title, sub, card, badge; } m_s{};
    QString m_textSize;
    bool m_single;
};
