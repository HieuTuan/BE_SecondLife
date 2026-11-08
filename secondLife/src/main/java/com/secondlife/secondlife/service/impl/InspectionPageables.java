package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.exception.BadRequestException;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

final class InspectionPageables {
    private InspectionPageables() {
    }

    static Pageable validate(Pageable pageable, Set<String> supportedFields) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!supportedFields.contains(order.getProperty())) {
                throw new BadRequestException("Trường sắp xếp không được hỗ trợ: " + order.getProperty());
            }
        }
        Sort sort = pageable.getSort().isUnsorted() ? Sort.by(Sort.Direction.DESC, "createdAt") : pageable.getSort();
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
