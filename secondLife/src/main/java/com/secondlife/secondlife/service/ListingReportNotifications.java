package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.ReportResponse;

public interface ListingReportNotifications {
    void created(ReportResponse report);

    void reviewStarted(ReportResponse report);

    void resolved(ReportResponse report);

}
