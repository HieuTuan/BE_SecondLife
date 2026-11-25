package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.PreListingInspectionQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PreListingInspectionQuoteRepository extends JpaRepository<PreListingInspectionQuote, UUID> {
}
