package com.nike.accel.ui.widgets.gauges;

/**
 * This interface lets a gauge business object communicate with the guage's UI component to
 * have it display data.
 */
public interface IGaugeUI {
    void setPointerSpeed(float value);
    void set7SegmentSpeed(String value);
    void set7SegmentLabelSpeed(String text);

    void setPointerBattery(float value);
    void set7SegmentBattery(String value);
    void set7SegmentLabelBattery(String text);

    void setPointerCurrent(float value);
    void set7SegmentCurrent(String value);
    void set7SegmentLabelCurrent(String text);

    void setMajorLabel(String text);
    void setIGauge(IGauge iGauge);
}
