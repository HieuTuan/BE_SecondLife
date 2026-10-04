package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.ShippingPickupAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ShippingPickupAddressRepository extends JpaRepository<ShippingPickupAddress,UUID> {}
