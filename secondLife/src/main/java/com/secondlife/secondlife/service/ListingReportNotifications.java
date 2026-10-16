package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.reputation.ReportResponse;
import com.secondlife.secondlife.dto.reputation.ReportRequests.Status;
import com.secondlife.secondlife.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class ListingReportNotifications {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public void created(ReportResponse report) {
        var reviewers=jdbc.query("""
            SELECT u.id FROM users u
            JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id
            LEFT JOIN role_permissions rp ON rp.role_id=r.id LEFT JOIN permissions p ON p.id=rp.permission_id
            WHERE u.account_status='ACTIVE' AND u.id<>? AND u.id<>?
            GROUP BY u.id
            HAVING (bool_or(r.code='STAFF') AND bool_or(p.code='STAFF_FRAUD_REVIEW'))
                OR (bool_or(r.code='ADMIN') AND bool_or(p.code='ADMIN_RBAC_MANAGE'))
            """,(rs,n)->rs.getObject("id",UUID.class),report.reporterId(),report.sellerId());
        for (var reviewer:reviewers) notifications.notifyInApp(reviewer,"Có báo cáo vi phạm bài đăng mới",
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
