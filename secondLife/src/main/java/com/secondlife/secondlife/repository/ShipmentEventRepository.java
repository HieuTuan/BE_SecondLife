package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent,UUID> {
    boolean existsByEventKey(String key);
    List<ShipmentEvent> findByShipmentIdOrderByOccurredAtAsc(UUID id);
}
