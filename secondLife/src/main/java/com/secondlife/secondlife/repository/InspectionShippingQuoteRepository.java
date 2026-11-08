package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionShippingQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InspectionShippingQuoteRepository extends JpaRepository<InspectionShippingQuote, UUID> {
}
