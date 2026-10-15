package com.secondlife.secondlife.service.payment;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class PaidOrderService {
    private final OrderRepository orderRepository;
    private final PostRepository postRepository;
    private final NegotiationRepository negotiationRepository;
    private final WalletService walletService;
    private final SystemWalletService systemWallet;
    private final SellerReputationService reputation;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    @Transactional(propagation=Propagation.MANDATORY)
    public void complete(Order order,boolean useWallet) {
        var post=order.getPost(); var buyer=order.getBuyer(); UUID buyerId=buyer.getId();
        var finalPrice=order.getFinalPrice(); var negotiation=order.getNegotiation();
        order.setStatus(OrderStatus.PROCESSING); order.setEscrowStatus(EscrowStatus.HELD);
        systemWallet.hold(order);
        if (useWallet) walletService.processPayment(buyerId, finalPrice.add(order.getShippingFee()), order.getId());
        reputation.initializePaidOrder(order);
        orderRepository.save(order);

        // Update post status so it can't be bought again
        post.setStatus("SOLD");
        postRepository.save(post);

        // Update negotiation status
        if (negotiation != null) {
            negotiation.setStatus(NegotiationStatus.COMPLETED);
            negotiationRepository.save(negotiation);
        }

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                    buyer,
                    "Đặt hàng thành công",
                    "Đơn hàng #" + order.getId() + " cho sản phẩm '" + post.getTitle() + "' đã được tạo thành công và giữ tiền ký quỹ an toàn.",
                    "ORDER",
                    order.getId(),
                    "ORDER",
                    "/buyer/orders/" + order.getId(),
                    "Xác nhận đơn hàng #" + order.getId() + " - SecondLife",
                    "Đơn hàng #" + order.getId() + " cho sản phẩm '" + post.getTitle() + "' đã được tạo thành công với tổng số tiền " + finalPrice.add(order.getShippingFee()) + " VND. Khoản tiền đang được giữ an toàn trong ví ký quỹ SecondLife."
            );
            notificationService.notifyWithEmail(
                    post.getUser(),
                    "Bạn có đơn hàng mới!",
                    "Khách hàng vừa đặt đơn hàng #" + order.getId() + " cho sản phẩm '" + post.getTitle() + "'. Vui lòng xác nhận trước " + order.getConfirmationDeadline() + ".",
                    "ORDER",
                    order.getId(),
                    "ORDER",
                    "/seller/orders/" + order.getId(),
                    "Thông báo đơn hàng mới #" + order.getId() + " - SecondLife",
                    "Bạn vừa nhận được đơn hàng mới #" + order.getId() + " cho sản phẩm '" + post.getTitle() + "'. Vui lòng truy cập hệ thống để chuẩn bị và xác nhận đơn hàng."
            );
        }

        if (notificationService != null) {
            for (UUID staffId : userRepository.findActiveOrderReviewStaffIds()) {
                notificationService.notify(staffId,"Đơn hàng mới chờ SELLER xác nhận",
                    "Đơn hàng #"+order.getId()+" đã thanh toán, chờ người bán xác nhận trước "+order.getConfirmationDeadline(),
                    "ORDER",order.getId(),"ORDER","/staff/orders/"+order.getId());
            }
        }
    }
}
