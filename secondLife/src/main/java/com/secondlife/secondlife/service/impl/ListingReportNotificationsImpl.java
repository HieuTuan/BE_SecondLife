package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.ListingReportNotifications;
import com.secondlife.secondlife.service.NotificationService;

import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import com.secondlife.secondlife.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("listingReportNotifications") @RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class ListingReportNotificationsImpl implements ListingReportNotifications {
    private final UserRepository users;
    private final NotificationService notifications;

    public void created(ReportResponse report) {
        var recipients=users.findEligibleReviewers(report.reporterId(),report.sellerId());
        for (var reviewer:recipients) notifications.notifyInApp(reviewer,"Có báo cáo vi phạm bài đăng mới",
                "Một bài đăng được báo cáo. Vui lòng kiểm tra hồ sơ và bằng chứng trước khi kết luận.",
                NotificationType.REPORT,report.id(),"REPORT","/staff/reports/"+report.id());
    }

    public void reviewStarted(ReportResponse report) {
        notifications.notifyInApp(report.reporterId(),"Báo cáo bài đăng đang được xem xét",
                "Nhân viên đã tiếp nhận báo cáo của bạn và đang kiểm tra.",
                NotificationType.REPORT,report.id(),"REPORT","/reports/"+report.id());
    }

    public void resolved(ReportResponse report) {
        String message=report.status()==Status.CONFIRMED
                ? "Báo cáo của bạn đã được xác nhận sau khi xem xét bằng chứng."
                : "Báo cáo của bạn chưa được chấp nhận sau khi xem xét. Xem chi tiết kết quả xử lý.";
        notifications.notifyInApp(report.reporterId(),"Đã có kết quả xử lý báo cáo bài đăng",message,
                NotificationType.REPORT,report.id(),"REPORT","/reports/"+report.id());
    }
}
