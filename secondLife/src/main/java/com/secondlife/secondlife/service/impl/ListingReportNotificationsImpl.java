package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.ListingReportNotifications;
import com.secondlife.secondlife.service.NotificationService;
import lombok.RequiredArgsConstructor;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("listingReportNotifications")
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ListingReportNotificationsImpl implements ListingReportNotifications {
    private final UserRepository users;
    private final NotificationService notifications;

    public void created(ReportResponse report) {
        boolean orderReport = report.orderId() != null;
        for (var reviewer : users.findEligibleReviewers(report.reporterId(), report.sellerId())) {
            notifications.notifyInApp(reviewer,
                    orderReport ? "Có tranh chấp đơn hàng mới" : "Có báo cáo vi phạm bài đăng mới",
                    orderReport ? "Tiền đơn hàng đang được giữ để xem xét khiếu nại." :
                            "Một bài đăng được báo cáo. Vui lòng kiểm tra hồ sơ và bằng chứng trước khi kết luận.",
                    NotificationType.REPORT, report.id(), "REPORT", "/staff/reports/" + report.id());
        }
        if (orderReport) {
            notifications.notifyInApp(report.sellerId(), "Đơn hàng có khiếu nại",
                    "Đơn hàng đang được giữ tiền. Vào tab Khiếu Nại để xem hồ sơ và gửi phản hồi.",
                    NotificationType.REPORT, report.id(), "REPORT", null);
        }
    }

    @Override
    public void sellerResponseRequested(ReportResponse report) {
        String deadline = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(report.sellerResponseDeadlineAt());
        notifications.notifyInApp(report.sellerId(), "Cần phản hồi khiếu nại đơn hàng",
                "Vào tab Khiếu Nại và cung cấp phản hồi trước " + deadline
                        + " (giờ Việt Nam). Quá hạn 24 giờ sẽ ghi nhận trách nhiệm thuộc người bán.",
                NotificationType.REPORT, report.id(), "REPORT", null);
    }

    public void reviewStarted(ReportResponse report) {
        notifications.notifyInApp(report.reporterId(),
                report.orderId() == null ? "Báo cáo bài đăng đang được xem xét" : "Khiếu nại đơn hàng đang được xem xét",
                "Nhân viên đã tiếp nhận báo cáo của bạn và đang kiểm tra.",
                NotificationType.REPORT, report.id(), "REPORT", "/reports/" + report.id());
    }

    public void resolved(ReportResponse report) {
        String message = report.resolutionAction() == ResolutionAction.RETURN_AND_REFUND
                ? "Yêu cầu hoàn hàng đã được duyệt. Tiền hàng tiếp tục được giữ đến khi xử lý hoàn tiền."
                : report.status() == Status.CONFIRMED
                    ? "Báo cáo của bạn đã được xác nhận sau khi xem xét bằng chứng."
                    : "Báo cáo của bạn chưa được chấp nhận sau khi xem xét. Xem chi tiết kết quả xử lý.";
        notifications.notifyInApp(report.reporterId(),
                report.orderId() == null ? "Đã có kết quả xử lý báo cáo bài đăng" : "Đã có kết quả xử lý tranh chấp",
                message, NotificationType.REPORT, report.id(), "REPORT", "/reports/" + report.id());
        if (report.orderId() != null) {
            notifications.notifyInApp(report.sellerId(), "Đã có kết quả xử lý tranh chấp",
                    "Nhân viên đã cập nhật kết quả xử lý khiếu nại của đơn hàng.",
                    NotificationType.REPORT, report.id(), "REPORT", null);
        }
    }
}
