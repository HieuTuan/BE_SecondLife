package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.commission.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.CommissionRuleType;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.CommissionCalculator;
import com.secondlife.secondlife.service.CommissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {
    private final CommissionRuleRepository rules;
    private final CommissionRuleAuditRepository audits;
    private final OrderCommissionSnapshotRepository snapshots;
    private final CommissionCalculator calculator;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    @Override @Transactional(readOnly = true)
    public Page<CommissionRuleResponse> list(Pageable pageable) {
        return rules.findByType(CommissionRuleType.DEFAULT, pageable).map(CommissionRuleResponse::from);
    }
    @Override @Transactional(readOnly = true)
    public CommissionRuleResponse get(UUID id) { return CommissionRuleResponse.from(defaultRule(id)); }
    @Override @Transactional
    public CommissionRuleResponse create(UUID actorId, CommissionRuleRequest request) {
        lockPolicy(); validate(request, null);
        var rule = new CommissionRule(); rule.setCreatedAt(Instant.now()); rule.setRevision(1);
        apply(rule, actorId, request);
        rules.saveAndFlush(rule); audit(rule, actorId, "CREATE", null, request.reason());
        return CommissionRuleResponse.from(rule);
    }
    @Override @Transactional
    public CommissionRuleResponse update(UUID actorId, UUID id, CommissionRuleRequest request) {
        lockPolicy(); var rule = defaultRule(id); validate(request, id);
        String oldValue = json(rule); rule.setRevision(rule.getRevision() + 1); apply(rule, actorId, request);
        rules.saveAndFlush(rule); audit(rule, actorId, "UPDATE", oldValue, request.reason());
        return CommissionRuleResponse.from(rule);
    }
    @Override @Transactional
    public CommissionRuleResponse deactivate(UUID actorId, UUID id, String reason) {
        lockPolicy(); var rule = defaultRule(id); String oldValue = json(rule);
        if (rule.isActive()) {
            rule.setActive(false); rule.setRevision(rule.getRevision() + 1);
            rule.setUpdatedBy(actorId); rule.setUpdatedAt(Instant.now()); rules.saveAndFlush(rule);
            audit(rule, actorId, "DEACTIVATE", oldValue, reason);
        }
        return CommissionRuleResponse.from(rule);
    }
    @Override @Transactional(readOnly = true)
    public Page<CommissionAuditResponse> history(UUID id, Pageable pageable) {
        rule(id); return audits.findByRuleIdOrderByChangedAtDesc(id, pageable).map(CommissionAuditResponse::from);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public OrderCommissionSnapshot capture(Order order, UUID actorId, String reason) {
        // The caller locks the order/post. Serialize with policy writes across all app instances.
        lockPolicy();
        var existing = snapshots.findByOrderId(order.getId());
        if (existing.isPresent()) return existing.get();
        UUID categoryId = order.getPost().getCategoryId();
        BigDecimal price = order.getFinalPrice();
        if (price == null || price.signum() <= 0) throw new ConflictException("Order price must be positive");
        var selected = rules.findByTypeAndActiveTrue(CommissionRuleType.DEFAULT)
                .orElseThrow(() -> new ConflictException("Default commission policy is not configured"));
        var snapshot = new OrderCommissionSnapshot();
        snapshot.setOrderId(order.getId()); snapshot.setRuleId(selected.getId()); snapshot.setRuleRevision(selected.getRevision());
        snapshot.setRuleName(selected.getName()); snapshot.setRuleType(selected.getType()); snapshot.setCategoryId(categoryId);
        snapshot.setTransactionValueFrom(selected.getTransactionValueFrom()); snapshot.setTransactionValueTo(selected.getTransactionValueTo());
        snapshot.setRate(selected.getRate()); snapshot.setMinCommission(selected.getMinCommission()); snapshot.setMaxCommission(selected.getMaxCommission());
        snapshot.setBaseType("PRODUCT_PRICE"); snapshot.setCommissionBase(price); snapshot.setCurrency("VND");
        snapshot.setSnapshottedAt(Instant.now()); snapshot.setCapturedBy(actorId); snapshot.setReason(reason);
        return snapshots.saveAndFlush(snapshot);
    }
    @Override @Transactional(readOnly = true)
    public OrderCommissionSnapshot requireSnapshot(UUID orderId) {
        return snapshots.findByOrderId(orderId).orElseThrow(() -> new ConflictException(
                "Order has no commission snapshot; an ADMIN must capture a policy for this legacy order before settlement"));
    }
    @Override public OrderCommissionResponse describe(OrderCommissionSnapshot s) {
        var amounts = calculator.calculate(s.getCommissionBase(), s.getRate(), s.getMinCommission(), s.getMaxCommission());
        return new OrderCommissionResponse(s.getId(), s.getOrderId(), s.getRuleId(), s.getRuleRevision(), s.getRuleName(),
                s.getRuleType(), s.getCategoryId(), s.getTransactionValueFrom(), s.getTransactionValueTo(), s.getRate(),
                s.getMinCommission(), s.getMaxCommission(), s.getBaseType(), s.getCommissionBase(), amounts.rawCommission(),
                amounts.platformCommission(), amounts.sellerPayout(), s.getCurrency(), s.getSnapshottedAt(), s.getCapturedBy(), s.getReason());
    }
    private CommissionRule rule(UUID id) { return rules.findById(id).orElseThrow(() -> new NotFoundException("Commission rule not found")); }
    private CommissionRule defaultRule(UUID id) {
        var rule = rule(id);
        if (rule.getType() != CommissionRuleType.DEFAULT)
            throw new ConflictException("Only default commission policies can be managed");
        return rule;
    }
    private void lockPolicy() { jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {}, 0x534C434F4D4D4953L); }
    private void apply(CommissionRule rule, UUID actor, CommissionRuleRequest r) {
        rule.setName(r.name().trim()); rule.setType(CommissionRuleType.DEFAULT); rule.setCategoryId(null);
        rule.setTransactionValueFrom(null); rule.setTransactionValueTo(null);
        rule.setRate(r.rate()); rule.setMinCommission(r.minCommission()); rule.setMaxCommission(r.maxCommission());
        rule.setActive(r.active()); rule.setUpdatedBy(actor); rule.setUpdatedAt(Instant.now());
    }
    private void validate(CommissionRuleRequest r, UUID excludedId) {
        if (r.maxCommission() != null && r.maxCommission().compareTo(r.minCommission()) < 0)
            throw new BadRequestException("maxCommission must be >= minCommission");
        if (!r.active()) return;
        rules.findByTypeAndActiveTrue(CommissionRuleType.DEFAULT).filter(other -> !other.getId().equals(excludedId))
                .ifPresent(other -> { throw new ConflictException("An active default commission policy already exists"); });
    }
    private String json(CommissionRule rule) { return mapper.writeValueAsString(CommissionRuleResponse.from(rule)); }
    private void audit(CommissionRule rule, UUID actor, String action, String old, String reason) {
        var audit = new CommissionRuleAudit(); audit.setRuleId(rule.getId()); audit.setActorId(actor);
        audit.setAction(action); audit.setOldValue(old); audit.setNewValue(json(rule));
        audit.setReason(reason.trim()); audit.setChangedAt(Instant.now()); audits.save(audit);
    }
}
