package com.secondlife.secondlife.enums;

import java.time.LocalTime;

public enum InspectionAppointmentShift {
    MORNING(LocalTime.of(8, 0), LocalTime.NOON),
    AFTERNOON(LocalTime.of(13, 0), LocalTime.of(17, 0));

    private final LocalTime start;
    private final LocalTime end;

    InspectionAppointmentShift(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}
