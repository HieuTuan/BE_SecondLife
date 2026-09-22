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
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #f4f4f5;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #09090b; padding: 48px 16px;">
                    <tr>
                        <td align="center">
                            <!-- Outer Card with specular reflective rim -->
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #121214; border-radius: 16px; overflow: hidden; border: 1px solid #27272a; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px -5px rgba(255, 255, 255, 0.03);">
                                <!-- Top Reflective Specular Light Bar -->
                                <tr>
                                    <td height="2" style="background: linear-gradient(90deg, rgba(255,255,255,0) 0%%, rgba(255,255,255,0.75) 50%%, rgba(255,255,255,0) 100%%); font-size: 0; line-height: 0;">&nbsp;</td>
                                </tr>
                                <!-- Luxury Brand Header -->
                                <tr>
                                    <td style="background-color: #09090b; padding: 36px 40px 30px; text-align: center; border-bottom: 1px solid #1f1f23;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 800; letter-spacing: 6px; text-transform: uppercase;">SECOND LIFE</h1>
                                        <p style="margin: 8px 0 0 0; color: #a1a1aa; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; font-weight: 500;">PREMIUM RECOMMERCE &amp; AUTHENTICATION</p>
                                    </td>
                                </tr>
                                <!-- Main Body Content -->
                                <tr>
                                    <td style="padding: 40px 40px 36px;">
                                        <div style="display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; color: #e4e4e7; font-size: 11px; letter-spacing: 1.5px; text-transform: uppercase; font-weight: 600; margin-bottom: 20px;">
                                            XÁC THỰC DANH TÍNH
                                        </div>
                                        <h2 style="margin: 0 0 16px 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.3px;">Xác thực địa chỉ email</h2>
                                        <p style="margin: 0 0 16px 0; color: #d4d4d8; font-size: 15px; line-height: 1.6;">Kính chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 28px 0; color: #a1a1aa; font-size: 14px; line-height: 1.7;">Cảm ơn quý khách đã đăng ký trải nghiệm tại SecondLife. Để bảo vệ an toàn cho tài khoản và hoàn tất quá trình kích hoạt, vui lòng sử dụng mã bảo mật OTP dưới đây:</p>
                                        
                                        <!-- Reflective Glossy OTP Box -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 32px 0;">
                                            <tr>
                                                <td align="center" style="background: linear-gradient(180deg, #18181b 0%%, #0f0f11 100%%); border: 1px solid #3f3f46; border-radius: 12px; padding: 28px 20px; box-shadow: inset 0 1px 1px rgba(255,255,255,0.2), 0 10px 25px -5px rgba(0,0,0,0.5);">
                                                    <span style="display: block; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 2.5px; color: #a1a1aa; margin-bottom: 12px;">MÃ BẢO MẬT OTP CỦA BẠN</span>
                                                    <span style="display: block; font-size: 38px; font-weight: 800; letter-spacing: 14px; color: #ffffff; font-family: 'SF Mono', Consolas, Monaco, monospace; text-shadow: 0 0 20px rgba(255,255,255,0.35); padding-left: 14px;">%s</span>
                                                    <span style="display: inline-block; margin-top: 14px; padding: 3px 10px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.12); border-radius: 12px; font-size: 12px; color: #d4d4d8; font-weight: 500;">⏱ Hiệu lực trong 15 phút</span>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Sleek Monochrome Caution Box -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background: #18181b; border-left: 3px solid #ffffff; border-radius: 6px; padding: 14px 18px; margin-bottom: 28px;">
                                            <tr>
                                                <td style="color: #d4d4d8; font-size: 13px; line-height: 1.6;">
                                                    <strong style="color: #ffffff;">Lưu ý bảo mật:</strong> Mã OTP có tính chất bảo mật tuyệt đối. Đội ngũ SecondLife không bao giờ yêu cầu quý khách cung cấp mã này dưới bất kỳ hình thức nào.
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #71717a; font-size: 13px; line-height: 1.6;">Nếu quý khách không yêu cầu đăng ký tài khoản này, xin vui lòng bỏ qua thư này.</p>
                                    </td>
                                </tr>
                                <!-- Luxury Minimalist Footer -->
                                <tr>
                                    <td style="background-color: #09090b; padding: 28px 40px; border-top: 1px solid #1f1f23; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #d4d4d8; font-size: 12px; font-weight: 600; letter-spacing: 1px; text-transform: uppercase;">SecondLife Client Care</p>
                                        <p style="margin: 0; color: #71717a; font-size: 12px;">Email: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 12px 0 0 0; color: #52525b; font-size: 11px; letter-spacing: 0.5px;">&copy; 2026 SecondLife Inc. All rights reserved.</p>
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
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #f4f4f5;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #09090b; padding: 48px 16px;">
                    <tr>
                        <td align="center">
                            <!-- Outer Card with specular reflective rim -->
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #121214; border-radius: 16px; overflow: hidden; border: 1px solid #27272a; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px -5px rgba(255, 255, 255, 0.03);">
                                <!-- Top Reflective Specular Light Bar -->
                                <tr>
                                    <td height="2" style="background: linear-gradient(90deg, rgba(255,255,255,0) 0%%, rgba(255,255,255,0.75) 50%%, rgba(255,255,255,0) 100%%); font-size: 0; line-height: 0;">&nbsp;</td>
                                </tr>
                                <!-- Luxury Brand Header -->
                                <tr>
                                    <td style="background-color: #09090b; padding: 36px 40px 30px; text-align: center; border-bottom: 1px solid #1f1f23;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 800; letter-spacing: 6px; text-transform: uppercase;">SECOND LIFE</h1>
                                        <p style="margin: 8px 0 0 0; color: #a1a1aa; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; font-weight: 500;">ACCOUNT SECURITY PROTOCOL</p>
                                    </td>
                                </tr>
                                <!-- Main Body Content -->
                                <tr>
                                    <td style="padding: 40px 40px 36px;">
                                        <div style="display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; color: #e4e4e7; font-size: 11px; letter-spacing: 1.5px; text-transform: uppercase; font-weight: 600; margin-bottom: 20px;">
                                            THIẾT LẬP MẬT KHẨU
                                        </div>
                                        <h2 style="margin: 0 0 16px 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.3px;">Yêu cầu đặt lại mật khẩu</h2>
                                        <p style="margin: 0 0 16px 0; color: #d4d4d8; font-size: 15px; line-height: 1.6;">Kính chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 28px 0; color: #a1a1aa; font-size: 14px; line-height: 1.7;">Hệ thống nhận được yêu cầu khôi phục mật khẩu truy cập tài khoản SecondLife của quý khách. Vui lòng nhập mã OTP bảo mật dưới đây để hoàn tất:</p>
                                        
                                        <!-- Reflective Glossy OTP Box -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 32px 0;">
                                            <tr>
                                                <td align="center" style="background: linear-gradient(180deg, #18181b 0%%, #0f0f11 100%%); border: 1px solid #3f3f46; border-radius: 12px; padding: 28px 20px; box-shadow: inset 0 1px 1px rgba(255,255,255,0.2), 0 10px 25px -5px rgba(0,0,0,0.5);">
                                                    <span style="display: block; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 2.5px; color: #a1a1aa; margin-bottom: 12px;">MÃ OTP KHÔI PHỤC</span>
                                                    <span style="display: block; font-size: 38px; font-weight: 800; letter-spacing: 14px; color: #ffffff; font-family: 'SF Mono', Consolas, Monaco, monospace; text-shadow: 0 0 20px rgba(255,255,255,0.35); padding-left: 14px;">%s</span>
                                                    <span style="display: inline-block; margin-top: 14px; padding: 3px 10px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.12); border-radius: 12px; font-size: 12px; color: #d4d4d8; font-weight: 500;">⏱ Hiệu lực trong 15 phút</span>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Sleek Monochrome Alert Box -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background: #18181b; border-left: 3px solid #ffffff; border-radius: 6px; padding: 14px 18px; margin-bottom: 28px;">
                                            <tr>
                                                <td style="color: #d4d4d8; font-size: 13px; line-height: 1.6;">
                                                    <strong style="color: #ffffff;">Cảnh báo an toàn:</strong> Nếu quý khách không thực hiện yêu cầu này, có thể ai đó đang cố gắng truy cập tài khoản. Xin hãy liên hệ với bộ phận hỗ trợ ngay lập tức.
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #71717a; font-size: 13px; line-height: 1.6;">Trân trọng,<br><strong style="color: #d4d4d8;">Ban Quản Trị SecondLife</strong></p>
                                    </td>
                                </tr>
                                <!-- Luxury Minimalist Footer -->
                                <tr>
                                    <td style="background-color: #09090b; padding: 28px 40px; border-top: 1px solid #1f1f23; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #d4d4d8; font-size: 12px; font-weight: 600; letter-spacing: 1px; text-transform: uppercase;">SecondLife Client Care</p>
                                        <p style="margin: 0; color: #71717a; font-size: 12px;">Email: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 12px 0 0 0; color: #52525b; font-size: 11px; letter-spacing: 0.5px;">&copy; 2026 SecondLife Inc. All rights reserved.</p>
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
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #f4f4f5;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #09090b; padding: 48px 16px;">
                    <tr>
                        <td align="center">
                            <!-- Outer Card with specular reflective rim -->
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #121214; border-radius: 16px; overflow: hidden; border: 1px solid #27272a; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px -5px rgba(255, 255, 255, 0.03);">
                                <tr>
                                    <td height="2" style="background: linear-gradient(90deg, rgba(255,255,255,0) 0%%, rgba(255,255,255,0.75) 50%%, rgba(255,255,255,0) 100%%); font-size: 0; line-height: 0;">&nbsp;</td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 36px 40px 30px; text-align: center; border-bottom: 1px solid #1f1f23;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 800; letter-spacing: 6px; text-transform: uppercase;">SECOND LIFE</h1>
                                        <p style="margin: 8px 0 0 0; color: #a1a1aa; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; font-weight: 500;">VERIFIED SELLER PRIVILEGE</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px 40px 36px;">
                                        <div style="display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; color: #e4e4e7; font-size: 11px; letter-spacing: 1.5px; text-transform: uppercase; font-weight: 600; margin-bottom: 20px;">
                                            PHÊ DUYỆT THÀNH CÔNG
                                        </div>
                                        <h2 style="margin: 0 0 16px 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.3px;">Hồ sơ Người bán đã được kích hoạt</h2>
                                        <p style="margin: 0 0 16px 0; color: #d4d4d8; font-size: 15px; line-height: 1.6;">Kính chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 24px 0; color: #a1a1aa; font-size: 14px; line-height: 1.7;">Ban quản trị SecondLife trân trọng thông báo hồ sơ định danh Người bán (Seller) của quý khách đã được thẩm định và phê duyệt thành công.</p>
                                        
                                        <!-- Reflective Feature Box -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 28px 0;">
                                            <tr>
                                                <td style="background: linear-gradient(180deg, #18181b 0%%, #0f0f11 100%%); border: 1px solid #3f3f46; border-radius: 12px; padding: 24px; box-shadow: inset 0 1px 1px rgba(255,255,255,0.15);">
                                                    <h3 style="margin: 0 0 12px 0; color: #ffffff; font-size: 15px; font-weight: 700; letter-spacing: 0.5px; text-transform: uppercase;">Đặc quyền Người bán chính thức:</h3>
                                                    <ul style="margin: 0; padding-left: 20px; color: #d4d4d8; font-size: 14px; line-height: 1.8;">
                                                        <li>Đăng bán và quản trị sản phẩm trên nền tảng SecondLife.</li>
                                                        <li>Yêu cầu kiểm định tiêu chuẩn tại các Trung tâm Kiểm định chính thức.</li>
                                                        <li>Tiếp cận mạng lưới khách hàng cao cấp và uy tín toàn quốc.</li>
                                                    </ul>
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #71717a; font-size: 14px; line-height: 1.6;">Quý khách có thể đăng nhập ngay để bắt đầu trải nghiệm toàn bộ tiện ích dành riêng cho Người bán.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 28px 40px; border-top: 1px solid #1f1f23; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #d4d4d8; font-size: 12px; font-weight: 600; letter-spacing: 1px; text-transform: uppercase;">SecondLife Partner Relations</p>
                                        <p style="margin: 0; color: #71717a; font-size: 12px;">Email: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 12px 0 0 0; color: #52525b; font-size: 11px; letter-spacing: 0.5px;">&copy; 2026 SecondLife Inc. All rights reserved.</p>
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
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #f4f4f5;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #09090b; padding: 48px 16px;">
                    <tr>
                        <td align="center">
                            <!-- Outer Card with specular reflective rim -->
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #121214; border-radius: 16px; overflow: hidden; border: 1px solid #27272a; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px -5px rgba(255, 255, 255, 0.03);">
                                <tr>
                                    <td height="2" style="background: linear-gradient(90deg, rgba(255,255,255,0) 0%%, rgba(255,255,255,0.75) 50%%, rgba(255,255,255,0) 100%%); font-size: 0; line-height: 0;">&nbsp;</td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 36px 40px 30px; text-align: center; border-bottom: 1px solid #1f1f23;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 800; letter-spacing: 6px; text-transform: uppercase;">SECOND LIFE</h1>
                                        <p style="margin: 8px 0 0 0; color: #a1a1aa; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; font-weight: 500;">VERIFICATION REVIEW NOTICE</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px 40px 36px;">
                                        <div style="display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; color: #e4e4e7; font-size: 11px; letter-spacing: 1.5px; text-transform: uppercase; font-weight: 600; margin-bottom: 20px;">
                                            KẾT QUẢ THẨM ĐỊNH
                                        </div>
                                        <h2 style="margin: 0 0 16px 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.3px;">Thông báo xét duyệt hồ sơ Người bán</h2>
                                        <p style="margin: 0 0 16px 0; color: #d4d4d8; font-size: 15px; line-height: 1.6;">Kính chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 24px 0; color: #a1a1aa; font-size: 14px; line-height: 1.7;">Cảm ơn quý khách đã quan tâm đăng ký trở thành Người bán trên SecondLife. Sau khi thẩm tra hồ sơ, yêu cầu của quý khách tạm thời chưa được phê duyệt với lý do sau:</p>
                                        
                                        <!-- Reflective Dark Rejection Box -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 24px 0;">
                                            <tr>
                                                <td style="background: #18181b; border: 1px solid #3f3f46; border-left: 3px solid #71717a; border-radius: 8px; padding: 20px;">
                                                    <span style="display: block; font-size: 11px; font-weight: 700; color: #a1a1aa; text-transform: uppercase; letter-spacing: 1.5px; margin-bottom: 8px;">LÝ DO TỪ CHỐI:</span>
                                                    <span style="display: block; font-size: 14px; color: #ffffff; line-height: 1.6;">%s</span>
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0 0 14px 0; color: #a1a1aa; font-size: 14px; line-height: 1.6;">Quý khách vui lòng chụp lại ảnh định danh rõ nét, đối chiếu lại số giấy tờ và gửi lại yêu cầu xác minh bất cứ lúc nào trong mục Cài đặt Tài khoản.</p>
                                        <p style="margin: 0; color: #71717a; font-size: 13px; line-height: 1.6;">Nếu cần thêm trợ giúp, quý khách vui lòng phản hồi email này để được tư vấn chi tiết.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 28px 40px; border-top: 1px solid #1f1f23; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #d4d4d8; font-size: 12px; font-weight: 600; letter-spacing: 1px; text-transform: uppercase;">SecondLife Partner Relations</p>
                                        <p style="margin: 0; color: #71717a; font-size: 12px;">Email: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 12px 0 0 0; color: #52525b; font-size: 11px; letter-spacing: 0.5px;">&copy; 2026 SecondLife Inc. All rights reserved.</p>
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
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #f4f4f5;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #09090b; padding: 48px 16px;">
                    <tr>
                        <td align="center">
                            <!-- Outer Card with specular reflective rim -->
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #121214; border-radius: 16px; overflow: hidden; border: 1px solid #27272a; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px -5px rgba(255, 255, 255, 0.03);">
                                <tr>
                                    <td height="2" style="background: linear-gradient(90deg, rgba(255,255,255,0) 0%%, rgba(255,255,255,0.75) 50%%, rgba(255,255,255,0) 100%%); font-size: 0; line-height: 0;">&nbsp;</td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 36px 40px 30px; text-align: center; border-bottom: 1px solid #1f1f23;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 800; letter-spacing: 6px; text-transform: uppercase;">SECOND LIFE</h1>
                                        <p style="margin: 8px 0 0 0; color: #a1a1aa; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; font-weight: 500;">CRITICAL SECURITY ALERT</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px 40px 36px;">
                                        <div style="display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; color: #e4e4e7; font-size: 11px; letter-spacing: 1.5px; text-transform: uppercase; font-weight: 600; margin-bottom: 20px;">
                                            CẢNH BÁO BẢO MẬT
                                        </div>
                                        <h2 style="margin: 0 0 16px 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.3px;">Mật khẩu tài khoản đã được thay đổi</h2>
                                        <p style="margin: 0 0 16px 0; color: #d4d4d8; font-size: 15px; line-height: 1.6;">Kính chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 24px 0; color: #a1a1aa; font-size: 14px; line-height: 1.7;">Hệ thống thông báo mật khẩu truy cập của quý khách vừa được cập nhật thành công và toàn bộ phiên đăng nhập trên các thiết bị khác đã được thu hồi vì mục đích an toàn.</p>
                                        
                                        <!-- Reflective Warning Box -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background: #18181b; border: 1px solid #3f3f46; border-left: 3px solid #ffffff; border-radius: 8px; padding: 18px 20px; margin: 24px 0;">
                                            <tr>
                                                <td style="color: #d4d4d8; font-size: 13px; line-height: 1.6;">
                                                    <strong style="color: #ffffff;">Hành động khẩn cấp:</strong> Nếu quý khách không thực hiện thay đổi này, tài khoản của quý khách có thể đã bị can thiệp trái phép. Vui lòng sử dụng tính năng <em>Quên mật khẩu</em> ngay lập tức hoặc liên hệ trung tâm hỗ trợ của chúng tôi.
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #71717a; font-size: 13px; line-height: 1.6;">Trân trọng,<br><strong style="color: #d4d4d8;">Đội Ngũ An Ninh Hệ Thống SecondLife</strong></p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 28px 40px; border-top: 1px solid #1f1f23; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #d4d4d8; font-size: 12px; font-weight: 600; letter-spacing: 1px; text-transform: uppercase;">SecondLife Cyber Security</p>
                                        <p style="margin: 0; color: #71717a; font-size: 12px;">Email: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 12px 0 0 0; color: #52525b; font-size: 11px; letter-spacing: 0.5px;">&copy; 2026 SecondLife Inc. All rights reserved.</p>
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
            case "LOCKED" -> "bị tạm khóa vì lý do an toàn bảo mật hoặc vi phạm chính sách nền tảng";
            case "DISABLED" -> "bị vô hiệu hóa bởi Quản trị viên hệ thống";
            case "ACTIVE" -> "đã được kích hoạt và hoạt động bình thường trở lại";
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
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #f4f4f5;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #09090b; padding: 48px 16px;">
                    <tr>
                        <td align="center">
                            <!-- Outer Card with specular reflective rim -->
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #121214; border-radius: 16px; overflow: hidden; border: 1px solid #27272a; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px -5px rgba(255, 255, 255, 0.03);">
                                <tr>
                                    <td height="2" style="background: linear-gradient(90deg, rgba(255,255,255,0) 0%%, rgba(255,255,255,0.75) 50%%, rgba(255,255,255,0) 100%%); font-size: 0; line-height: 0;">&nbsp;</td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 36px 40px 30px; text-align: center; border-bottom: 1px solid #1f1f23;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 800; letter-spacing: 6px; text-transform: uppercase;">SECOND LIFE</h1>
                                        <p style="margin: 8px 0 0 0; color: #a1a1aa; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; font-weight: 500;">ACCOUNT STATUS UPDATE</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px 40px 36px;">
                                        <div style="display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; color: #e4e4e7; font-size: 11px; letter-spacing: 1.5px; text-transform: uppercase; font-weight: 600; margin-bottom: 20px;">
                                            THÔNG BÁO HỆ THỐNG
                                        </div>
                                        <h2 style="margin: 0 0 16px 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.3px;">Cập nhật trạng thái tài khoản</h2>
                                        <p style="margin: 0 0 16px 0; color: #d4d4d8; font-size: 15px; line-height: 1.6;">Kính chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 20px 0; color: #a1a1aa; font-size: 14px; line-height: 1.7;">Chúng tôi xin thông báo tài khoản của quý khách tại SecondLife hiện <strong>%s</strong>.</p>
                                        
                                        <p style="margin: 24px 0 0 0; color: #71717a; font-size: 13px; line-height: 1.6;">Nếu quý khách có bất kỳ thắc mắc nào hoặc muốn khiếu nại quyết định này, xin vui lòng gửi phản hồi về địa chỉ email hỗ trợ chính thức của chúng tôi.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 28px 40px; border-top: 1px solid #1f1f23; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #d4d4d8; font-size: 12px; font-weight: 600; letter-spacing: 1px; text-transform: uppercase;">SecondLife Client Care</p>
                                        <p style="margin: 0; color: #71717a; font-size: 12px;">Email: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 12px 0 0 0; color: #52525b; font-size: 11px; letter-spacing: 0.5px;">&copy; 2026 SecondLife Inc. All rights reserved.</p>
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
            <body style="margin: 0; padding: 0; background-color: #09090b; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; -webkit-font-smoothing: antialiased; color: #f4f4f5;">
                <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #09090b; padding: 48px 16px;">
                    <tr>
                        <td align="center">
                            <!-- Outer Card with specular reflective rim -->
                            <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #121214; border-radius: 16px; overflow: hidden; border: 1px solid #27272a; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7), 0 0 35px -5px rgba(255, 255, 255, 0.03);">
                                <tr>
                                    <td height="2" style="background: linear-gradient(90deg, rgba(255,255,255,0) 0%%, rgba(255,255,255,0.75) 50%%, rgba(255,255,255,0) 100%%); font-size: 0; line-height: 0;">&nbsp;</td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 36px 40px 30px; text-align: center; border-bottom: 1px solid #1f1f23;">
                                        <h1 style="margin: 0; color: #ffffff; font-size: 24px; font-weight: 800; letter-spacing: 6px; text-transform: uppercase;">SECOND LIFE</h1>
                                        <p style="margin: 8px 0 0 0; color: #a1a1aa; font-size: 11px; letter-spacing: 3px; text-transform: uppercase; font-weight: 500;">INSPECTION CENTER PARTNERSHIP</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px 40px 36px;">
                                        <div style="display: inline-block; padding: 4px 12px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; color: #e4e4e7; font-size: 11px; letter-spacing: 1.5px; text-transform: uppercase; font-weight: 600; margin-bottom: 20px;">
                                            ĐỐI TÁC KIỂM ĐỊNH
                                        </div>
                                        <h2 style="margin: 0 0 16px 0; color: #ffffff; font-size: 22px; font-weight: 700; letter-spacing: -0.3px;">Khởi tạo tài khoản Trung tâm Kiểm định</h2>
                                        <p style="margin: 0 0 16px 0; color: #d4d4d8; font-size: 15px; line-height: 1.6;">Kính chào <strong>%s</strong>,</p>
                                        <p style="margin: 0 0 24px 0; color: #a1a1aa; font-size: 14px; line-height: 1.7;">Ban quản trị SecondLife vừa khởi tạo tài khoản vận hành Trung tâm Kiểm định chất lượng cho đơn vị của quý khách với thông tin đăng nhập như sau:</p>
                                        
                                        <!-- Reflective Credentials Frame -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="margin: 28px 0;">
                                            <tr>
                                                <td style="background: linear-gradient(180deg, #18181b 0%%, #0f0f11 100%%); border: 1px solid #3f3f46; border-radius: 12px; padding: 24px; box-shadow: inset 0 1px 1px rgba(255,255,255,0.2);">
                                                    <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0">
                                                        <tr>
                                                            <td style="color: #a1a1aa; font-size: 13px; padding-bottom: 10px;">Tài khoản đăng nhập:</td>
                                                            <td style="color: #ffffff; font-size: 14px; font-weight: 600; padding-bottom: 10px; font-family: monospace;">%s</td>
                                                        </tr>
                                                        <tr>
                                                            <td style="color: #a1a1aa; font-size: 13px;">Mật khẩu ban đầu:</td>
                                                            <td style="color: #ffffff; font-size: 15px; font-weight: 700; font-family: 'SF Mono', Consolas, monospace; letter-spacing: 1px;">
                                                                <span style="background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.2); padding: 4px 10px; border-radius: 6px;">%s</span>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Sleek Monochrome Caution Box -->
                                        <table role="presentation" width="100%%" border="0" cellspacing="0" cellpadding="0" style="background: #18181b; border-left: 3px solid #ffffff; border-radius: 6px; padding: 14px 18px; margin-bottom: 24px;">
                                            <tr>
                                                <td style="color: #d4d4d8; font-size: 13px; line-height: 1.6;">
                                                    <strong style="color: #ffffff;">Lưu ý bảo mật:</strong> Vui lòng đăng nhập và đổi lại mật khẩu ngay trong lần sử dụng đầu tiên để đảm bảo tiêu chuẩn bảo mật hệ thống.
                                                </td>
                                            </tr>
                                        </table>

                                        <p style="margin: 0; color: #71717a; font-size: 13px; line-height: 1.6;">Trân trọng,<br><strong style="color: #d4d4d8;">Ban Quản Trị Hệ Thống SecondLife</strong></p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #09090b; padding: 28px 40px; border-top: 1px solid #1f1f23; text-align: center;">
                                        <p style="margin: 0 0 6px 0; color: #d4d4d8; font-size: 12px; font-weight: 600; letter-spacing: 1px; text-transform: uppercase;">SecondLife Partner Relations</p>
                                        <p style="margin: 0; color: #71717a; font-size: 12px;">Email: support@secondlife.com &bull; Hotline: 1900 xxxx</p>
                                        <p style="margin: 12px 0 0 0; color: #52525b; font-size: 11px; letter-spacing: 0.5px;">&copy; 2026 SecondLife Inc. All rights reserved.</p>
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
