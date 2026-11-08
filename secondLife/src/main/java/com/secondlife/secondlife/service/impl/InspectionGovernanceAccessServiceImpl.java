package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionGovernanceAccessService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionGovernanceAccessServiceImpl implements InspectionGovernanceAccessService {
    private final UserRepository users;

    @Override
    public User requireActiveUser(UUID actorId) {
        if (actorId == null) throw new ForbiddenException("Authentication is required");
        User actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("User not found"));
        if (actor.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenException("Tài khoản không hoạt động");
        }
        return actor;
    }

    @Override
    public User requireCenter(UUID actorId) {
        User actor = requireActiveUser(actorId);
        if (!actor.hasRole("INSPECTION_CENTER")) {
            throw new ForbiddenException("Chỉ trung tâm kiểm định được gửi đề xuất");
        }
        return actor;
    }

    @Override
    public User requireAdmin(UUID actorId) {
        User actor = requireActiveUser(actorId);
        if (!actor.hasRole("ADMIN")) throw new ForbiddenException("Chỉ Admin được xét duyệt");
        return actor;
    }

    @Override
    public void requireOrderRead(UUID actorId, InspectionOrder order) {
        User actor = requireActiveUser(actorId);
        if (actor.hasRole("ADMIN") || actor.hasRole("STAFF")
                || same(order.getBuyer(), actorId) || same(order.getSeller(), actorId)
                || same(order.getInspector(), actorId) || same(order.getInspectionCenter(), actorId)) return;
        User inspector = order.getInspector();
        if (inspector != null && same(inspector.getManager(), actorId)) return;
        if (order.getSeller() == null && order.getPost() != null && same(order.getPost().getUser(), actorId)) return;
        throw new ForbiddenException("Bạn không có quyền xem checklist của đơn kiểm định này");
    }

    private boolean same(User user, UUID id) {
        return user != null && id.equals(user.getId());
    }
}
