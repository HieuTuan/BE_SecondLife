package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.OrderCommissionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface OrderCommissionSnapshotRepository extends JpaRepository<OrderCommissionSnapshot, UUID> {
    Optional<OrderCommissionSnapshot> findByOrderId(UUID orderId);
}
