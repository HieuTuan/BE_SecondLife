package com.secondlife.secondlife.service.impl;

import org.springframework.stereotype.Component;

@Component
public class EmailTemplateBuilder {

    public String buildVerificationOtpEmail(String fullName, String otp) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "Quý khách";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Xác thực tài khoản SecondLife</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1e293b;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;">
                                <tr>
                                    <td style="background-color: #0f766e; padding: 32px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">SecondLife</h1>
                                        <p style="margin: 6px 0 0 0; color: #ccfbf1; font-size: 13px; letter-spacing: 0.5px; text-transform: uppercase; font-weight: 500;">Nền Tảng Đồ Cũ & Kiểm Định Chất Lượng</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Xác thực địa chỉ email</h2>
                                        <p style="margin: 0 0 16px 0; color: #334155; font-size: 15px; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 24px 0; color: #475569; font-size: 15px; line-height: 1.6;">Cảm ơn bạn đã đăng ký tài khoản tại SecondLife. Để hoàn tất quy trình kích hoạt tài khoản và bảo vệ quyền lợi của bạn, vui lòng nhập mã xác thực OTP dưới đây:</p>
                                        
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 28px 0;">
                                            <tr>
                                                <td align="center" style="background-color: #f0fdf4; border: 1.5px solid #0d9488; border-radius: 10px; padding: 24px 20px;">
                                                    <span style="display: block; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; color: #0f766e; margin-bottom: 8px;">Mã xác thực OTP của bạn</span>
                                                    <span style="display: block; font-size: 36px; font-weight: 800; letter-spacing: 12px; color: #0f766e; font-family: 'SF Mono', Consolas, Monaco, monospace;">%s</span>
                                                    <span style="display: inline-block; margin-top: 10px; font-size: 13px; color: #047857; font-weight: 500;">⏱ Mã có hiệu lực trong vòng 15 phút</span>
                                                </td>
                                            </tr>
                                        </table>

                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #fffbeb; border: 1px solid #fef3c7; border-radius: 8px; padding: 14px 18px; margin-bottom: 24px;">
                                            <tr>
                                                <td style="color: #92400e; font-size: 13px; line-height: 1.5;">
                                                    <strong>Lưu ý bảo mật:</strong> Tuyệt đối không cung cấp mã OTP này cho bất kỳ ai, kể cả nhân viên tự xưng là bộ phận hỗ trợ của SecondLife.
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #64748b; font-size: 14px; line-height: 1.5;">Nếu bạn không thực hiện đăng ký tài khoản này, xin vui lòng bỏ qua email.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8fafc; padding: 24px 40px; border-top: 1px solid #e2e8f0; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #64748b; font-size: 13px; font-weight: 500;">SecondLife Marketplace Support Team</p>
                                        <p style="margin: 0; color: #94a3b8; font-size: 12px;">Email hỗ trợ: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 10px 0 0 0; color: #cbd5e1; font-size: 11px;">&copy; 2026 SecondLife. Mọi quyền được bảo lưu.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(greetingName, otp);
    }

    public String buildPasswordResetOtpEmail(String fullName, String otp) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "Quý khách";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Yêu cầu đặt lại mật khẩu - SecondLife</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1e293b;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;">
                                <tr>
                                    <td style="background-color: #0f766e; padding: 32px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">SecondLife</h1>
                                        <p style="margin: 6px 0 0 0; color: #ccfbf1; font-size: 13px; letter-spacing: 0.5px; text-transform: uppercase; font-weight: 500;">Bảo Mật Tài Khoản</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Yêu cầu đặt lại mật khẩu</h2>
                                        <p style="margin: 0 0 16px 0; color: #334155; font-size: 15px; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 24px 0; color: #475569; font-size: 15px; line-height: 1.6;">Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn tại SecondLife. Dưới đây là mã xác thực OTP để tiến hành thiết lập mật khẩu mới:</p>
                                        
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 28px 0;">
                                            <tr>
                                                <td align="center" style="background-color: #f0fdf4; border: 1.5px solid #0d9488; border-radius: 10px; padding: 24px 20px;">
                                                    <span style="display: block; font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; color: #0f766e; margin-bottom: 8px;">Mã OTP đặt lại mật khẩu</span>
                                                    <span style="display: block; font-size: 36px; font-weight: 800; letter-spacing: 12px; color: #0f766e; font-family: 'SF Mono', Consolas, Monaco, monospace;">%s</span>
                                                    <span style="display: inline-block; margin-top: 10px; font-size: 13px; color: #047857; font-weight: 500;">⏱ Mã có hiệu lực trong vòng 15 phút</span>
                                                </td>
                                            </tr>
                                        </table>

                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #fffbeb; border: 1px solid #fef3c7; border-radius: 8px; padding: 14px 18px; margin-bottom: 24px;">
                                            <tr>
                                                <td style="color: #92400e; font-size: 13px; line-height: 1.5;">
                                                    <strong>Cảnh báo an toàn:</strong> Nếu bạn không thực hiện yêu cầu này, có thể ai đó đang cố gắng truy cập trái phép tài khoản của bạn. Vui lòng bỏ qua email này hoặc liên hệ ngay với bộ phận bảo mật của chúng tôi.
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #64748b; font-size: 14px; line-height: 1.5;">Trân trọng,<br><strong>Đội ngũ Quản trị SecondLife</strong></p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8fafc; padding: 24px 40px; border-top: 1px solid #e2e8f0; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #64748b; font-size: 13px; font-weight: 500;">SecondLife Marketplace Support Team</p>
                                        <p style="margin: 0; color: #94a3b8; font-size: 12px;">Email hỗ trợ: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 10px 0 0 0; color: #cbd5e1; font-size: 11px;">&copy; 2026 SecondLife. Mọi quyền được bảo lưu.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(greetingName, otp);
    }

    public String buildSellerVerificationApprovedEmail(String fullName) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "Quý đối tác";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Hồ sơ Người bán đã được phê duyệt - SecondLife</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1e293b;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;">
                                <tr>
                                    <td style="background-color: #0f766e; padding: 32px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">SecondLife</h1>
                                        <p style="margin: 6px 0 0 0; color: #ccfbf1; font-size: 13px; letter-spacing: 0.5px; text-transform: uppercase; font-weight: 500;">Thông Báo Đối Tác Bán Hàng</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Chúc mừng! Hồ sơ Người bán đã được duyệt</h2>
                                        <p style="margin: 0 0 16px 0; color: #334155; font-size: 15px; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 20px 0; color: #475569; font-size: 15px; line-height: 1.6;">Ban quản trị SecondLife vui mừng thông báo rằng hồ sơ định danh và đăng ký Người bán (Seller) của bạn đã được kiểm duyệt và phê duyệt thành công.</p>
                                        
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 24px 0;">
                                            <tr>
                                                <td style="background-color: #f0fdf4; border-left: 4px solid #0d9488; border-radius: 8px; padding: 18px 20px;">
                                                    <h3 style="margin: 0 0 8px 0; color: #065f46; font-size: 16px; font-weight: 600;">Quyền lợi Người bán chính thức:</h3>
                                                    <ul style="margin: 0; padding-left: 20px; color: #047857; font-size: 14px; line-height: 1.6;">
                                                        <li>Đăng bán sản phẩm đồ gia dụng cũ trên nền tảng.</li>
                                                        <li>Yêu cầu kiểm định chất lượng tại các Trung tâm Kiểm định chính thức.</li>
                                                        <li>Quản lý đơn hàng, kho và tương tác trực tiếp với người mua tin cậy.</li>
                                                    </ul>
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #64748b; font-size: 14px; line-height: 1.6;">Bây giờ bạn có thể đăng nhập vào tài khoản để bắt đầu trải nghiệm các tính năng dành cho Người bán.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8fafc; padding: 24px 40px; border-top: 1px solid #e2e8f0; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #64748b; font-size: 13px; font-weight: 500;">SecondLife Marketplace Support Team</p>
                                        <p style="margin: 0; color: #94a3b8; font-size: 12px;">Email hỗ trợ: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 10px 0 0 0; color: #cbd5e1; font-size: 11px;">&copy; 2026 SecondLife. Mọi quyền được bảo lưu.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(greetingName);
    }

    public String buildSellerVerificationRejectedEmail(String fullName, String rejectionReason) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "Quý khách";
        String reason = (rejectionReason != null && !rejectionReason.isBlank())
                ? rejectionReason
                : "Giấy tờ xác minh không đạt tiêu chuẩn chất lượng hình ảnh hoặc thông tin không khớp.";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Kết quả xét duyệt hồ sơ Người bán - SecondLife</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1e293b;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;">
                                <tr>
                                    <td style="background-color: #0f766e; padding: 32px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">SecondLife</h1>
                                        <p style="margin: 6px 0 0 0; color: #ccfbf1; font-size: 13px; letter-spacing: 0.5px; text-transform: uppercase; font-weight: 500;">Thông Báo Xét Duyệt Hồ Sơ</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Thông báo xét duyệt hồ sơ Người bán</h2>
                                        <p style="margin: 0 0 16px 0; color: #334155; font-size: 15px; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 20px 0; color: #475569; font-size: 15px; line-height: 1.6;">Cảm ơn bạn đã quan tâm và nộp hồ sơ trở thành Người bán trên nền tảng SecondLife. Sau khi kiểm duyệt, hồ sơ của bạn chưa đủ điều kiện phê duyệt với lý do sau:</p>
                                        
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 20px 0;">
                                            <tr>
                                                <td style="background-color: #fef2f2; border-left: 4px solid #ef4444; border-radius: 8px; padding: 18px 20px;">
                                                    <span style="display: block; font-size: 13px; font-weight: 600; color: #991b1b; text-transform: uppercase; margin-bottom: 6px;">Lý do từ chối:</span>
                                                    <span style="display: block; font-size: 15px; color: #b91c1c; line-height: 1.5;">%s</span>
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0 0 16px 0; color: #475569; font-size: 14px; line-height: 1.6;">Bạn có thể chụp lại hình ảnh giấy tờ rõ nét, kiểm tra lại số định danh cá nhân và gửi lại yêu cầu xác minh bất kỳ lúc nào tại mục Cài đặt Tài khoản.</p>
                                        <p style="margin: 0; color: #64748b; font-size: 14px; line-height: 1.5;">Nếu có bất kỳ thắc mắc nào, xin vui lòng phản hồi email này hoặc liên hệ hotline để được hỗ trợ.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8fafc; padding: 24px 40px; border-top: 1px solid #e2e8f0; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #64748b; font-size: 13px; font-weight: 500;">SecondLife Marketplace Support Team</p>
                                        <p style="margin: 0; color: #94a3b8; font-size: 12px;">Email hỗ trợ: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 10px 0 0 0; color: #cbd5e1; font-size: 11px;">&copy; 2026 SecondLife. Mọi quyền được bảo lưu.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(greetingName, reason);
    }

    public String buildPasswordChangedAlertEmail(String fullName) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "Quý khách";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Mật khẩu tài khoản vừa thay đổi - SecondLife</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1e293b;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;">
                                <tr>
                                    <td style="background-color: #0f766e; padding: 32px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">SecondLife</h1>
                                        <p style="margin: 6px 0 0 0; color: #ccfbf1; font-size: 13px; letter-spacing: 0.5px; text-transform: uppercase; font-weight: 500;">Cảnh Báo Bảo Mật</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Mật khẩu tài khoản đã được thay đổi</h2>
                                        <p style="margin: 0 0 16px 0; color: #334155; font-size: 15px; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 20px 0; color: #475569; font-size: 15px; line-height: 1.6;">Chúng tôi gửi email này để thông báo mật khẩu tài khoản của bạn tại SecondLife vừa được cập nhật thành công và các phiên đăng nhập khác đã được thu hồi vì lý do an toàn.</p>
                                        
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #fffbeb; border: 1px solid #fef3c7; border-radius: 8px; padding: 16px 20px; margin: 24px 0;">
                                            <tr>
                                                <td style="color: #92400e; font-size: 14px; line-height: 1.5;">
                                                    <strong>Quan trọng:</strong> Nếu bạn không thực hiện việc thay đổi mật khẩu này, có thể tài khoản của bạn đã bị truy cập trái phép. Vui lòng sử dụng tính năng <em>Quên mật khẩu</em> ngay lập tức hoặc liên hệ đội ngũ hỗ trợ của SecondLife.
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #64748b; font-size: 14px; line-height: 1.5;">Trân trọng,<br><strong>Đội ngũ Bảo mật SecondLife</strong></p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8fafc; padding: 24px 40px; border-top: 1px solid #e2e8f0; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #64748b; font-size: 13px; font-weight: 500;">SecondLife Marketplace Support Team</p>
                                        <p style="margin: 0; color: #94a3b8; font-size: 12px;">Email hỗ trợ: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 10px 0 0 0; color: #cbd5e1; font-size: 11px;">&copy; 2026 SecondLife. Mọi quyền được bảo lưu.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(greetingName);
    }

    public String buildAccountStatusChangedEmail(String fullName, String status) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "Quý khách";
        String statusDescription = switch (status != null ? status.toUpperCase() : "") {
            case "LOCKED" -> "bị tạm khóa vì lý do an toàn hoặc vi phạm chính sách nền tảng";
            case "DISABLED" -> "bị vô hiệu hóa bởi Quản trị viên hệ thống";
            case "ACTIVE" -> "đã được kích hoạt và hoạt động bình thường";
            default -> "vừa được cập nhật trạng thái mới: " + status;
        };

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Cập nhật trạng thái tài khoản - SecondLife</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1e293b;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;">
                                <tr>
                                    <td style="background-color: #0f766e; padding: 32px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">SecondLife</h1>
                                        <p style="margin: 6px 0 0 0; color: #ccfbf1; font-size: 13px; letter-spacing: 0.5px; text-transform: uppercase; font-weight: 500;">Thông Báo Quản Trị</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Thông báo trạng thái tài khoản</h2>
                                        <p style="margin: 0 0 16px 0; color: #334155; font-size: 15px; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 20px 0; color: #475569; font-size: 15px; line-height: 1.6;">Chúng tôi xin thông báo tài khoản của bạn tại SecondLife hiện <strong>%s</strong>.</p>
                                        
                                        <p style="margin: 20px 0 0 0; color: #64748b; font-size: 14px; line-height: 1.5;">Nếu bạn cần thêm thông tin chi tiết hoặc muốn khiếu nại quyết định này, vui lòng gửi phản hồi về hòm thư hỗ trợ của chúng tôi.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8fafc; padding: 24px 40px; border-top: 1px solid #e2e8f0; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #64748b; font-size: 13px; font-weight: 500;">SecondLife Marketplace Support Team</p>
                                        <p style="margin: 0; color: #94a3b8; font-size: 12px;">Email hỗ trợ: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 10px 0 0 0; color: #cbd5e1; font-size: 11px;">&copy; 2026 SecondLife. Mọi quyền được bảo lưu.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(greetingName, statusDescription);
    }

    public String buildInspectionCenterWelcomeEmail(String fullName, String email, String temporaryPassword) {
        String greetingName = (fullName != null && !fullName.isBlank()) ? fullName : "Quý đơn vị";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Tài khoản Trung tâm Kiểm định - SecondLife</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #1e293b;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05); border: 1px solid #e2e8f0;">
                                <tr>
                                    <td style="background-color: #0f766e; padding: 32px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">SecondLife</h1>
                                        <p style="margin: 6px 0 0 0; color: #ccfbf1; font-size: 13px; letter-spacing: 0.5px; text-transform: uppercase; font-weight: 500;">Trung Tâm Kiểm Định Chất Lượng</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="margin: 0 0 16px 0; color: #0f172a; font-size: 20px; font-weight: 600;">Thông tin khởi tạo tài khoản kiểm định</h2>
                                        <p style="margin: 0 0 16px 0; color: #334155; font-size: 15px; line-height: 1.6;">Xin chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 20px 0; color: #475569; font-size: 15px; line-height: 1.6;">Ban quản trị SecondLife vừa khởi tạo tài khoản vận hành Trung tâm Kiểm định cho đơn vị của bạn với thông tin đăng nhập như sau:</p>
                                        
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 20px; margin: 24px 0;">
                                            <tr>
                                                <td style="color: #334155; font-size: 14px; line-height: 1.8;">
                                                    <strong>Tên đăng nhập / Email:</strong> %s<br>
                                                    <strong>Mật khẩu ban đầu:</strong> <code style="background-color: #e2e8f0; padding: 2px 8px; border-radius: 4px; font-family: monospace; font-size: 15px; color: #0f766e;">%s</code>
                                                </td>
                                            </tr>
                                        </table>

                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #fffbeb; border: 1px solid #fef3c7; border-radius: 8px; padding: 14px 18px; margin-bottom: 24px;">
                                            <tr>
                                                <td style="color: #92400e; font-size: 13px; line-height: 1.5;">
                                                    <strong>Lưu ý bảo mật:</strong> Vui lòng đăng nhập và đổi lại mật khẩu ngay trong lần sử dụng đầu tiên để bảo vệ an toàn cho hệ thống.
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #64748b; font-size: 14px; line-height: 1.5;">Trân trọng,<br><strong>Ban Quản trị Hệ thống SecondLife</strong></p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8fafc; padding: 24px 40px; border-top: 1px solid #e2e8f0; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #64748b; font-size: 13px; font-weight: 500;">SecondLife Marketplace Support Team</p>
                                        <p style="margin: 0; color: #94a3b8; font-size: 12px;">Email hỗ trợ: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 10px 0 0 0; color: #cbd5e1; font-size: 11px;">&copy; 2026 SecondLife. Mọi quyền được bảo lưu.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(greetingName, email != null ? email : "", temporaryPassword != null ? temporaryPassword : "");
    }
}
