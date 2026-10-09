package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.OrderSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface OrderSettlementRepository extends JpaRepository<OrderSettlement, UUID> {
    Optional<OrderSettlement> findByOrderId(UUID orderId);
}
