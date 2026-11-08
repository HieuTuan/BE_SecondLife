package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.User;

import java.util.UUID;

public interface InspectionGovernanceAccessService {
    User requireCenter(UUID actorId);
    User requireAdmin(UUID actorId);
    User requireActiveUser(UUID actorId);
    void requireOrderRead(UUID actorId, InspectionOrder order);
}
