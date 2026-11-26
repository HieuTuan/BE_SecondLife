package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.BuyerDeliveryAddressRequest;
import com.secondlife.secondlife.dto.response.BuyerDeliveryAddressResponse;
import com.secondlife.secondlife.entity.BuyerDeliveryAddressEntity;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.BuyerDeliveryAddressRepository;
import com.secondlife.secondlife.repository.UserProfileRepository;
import com.secondlife.secondlife.service.BuyerAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service("buyerAddressService")
@RequiredArgsConstructor
public class BuyerAddressServiceImpl implements BuyerAddressService {

    private final BuyerDeliveryAddressRepository addressRepo;
    private final UserProfileRepository profileRepo;
    private final ObjectMapper mapper;

    @Override
    @Transactional
    public List<BuyerDeliveryAddressResponse> getAddresses(UUID userId) {
        return readOrInitializeAddresses(userId);
    }

    @Override
    @Transactional
    public BuyerDeliveryAddressResponse addAddress(UUID userId, BuyerDeliveryAddressRequest request) {
        List<BuyerDeliveryAddressResponse> list = readOrInitializeAddresses(userId);

        String id = (request.id() != null && !request.id().isBlank())
                ? request.id().strip()
                : "addr-" + UUID.randomUUID();

        boolean isDefault = list.isEmpty() || Boolean.TRUE.equals(request.isDefault());

        List<BuyerDeliveryAddressResponse> updated = new ArrayList<>();
        for (BuyerDeliveryAddressResponse existing : list) {
            if (isDefault && existing.isDefault()) {
                updated.add(new BuyerDeliveryAddressResponse(
                        existing.id(),
                        existing.name(),
                        existing.phone(),
                        existing.email(),
                        existing.streetAddress(),
                        existing.city(),
                        existing.latitude(),
                        existing.longitude(),
                        existing.addressType(),
                        false
                ));
            } else {
                updated.add(existing);
            }
        }

        BuyerDeliveryAddressResponse created = new BuyerDeliveryAddressResponse(
                id,
                request.name().strip(),
                request.phone().strip(),
                request.email() != null ? request.email().strip() : null,
                request.streetAddress().strip(),
                request.city() != null ? request.city().strip() : null,
                request.latitude(),
                request.longitude(),
                request.addressType() != null ? request.addressType().strip() : "home",
                isDefault
        );
        updated.add(created);

        persistAndSync(userId, updated);
        return created;
    }

    @Override
    @Transactional
    public BuyerDeliveryAddressResponse updateAddress(UUID userId, String addressId, BuyerDeliveryAddressRequest request) {
        List<BuyerDeliveryAddressResponse> list = readOrInitializeAddresses(userId);

        int targetIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).id(), addressId)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex == -1) {
            throw new NotFoundException("Delivery address not found: " + addressId);
        }

        BuyerDeliveryAddressResponse existing = list.get(targetIndex);
        boolean isDefault = Boolean.TRUE.equals(request.isDefault()) || (existing.isDefault() && list.size() == 1);

        List<BuyerDeliveryAddressResponse> updated = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BuyerDeliveryAddressResponse item = list.get(i);
            if (i == targetIndex) {
                updated.add(new BuyerDeliveryAddressResponse(
                        addressId,
                        request.name().strip(),
                        request.phone().strip(),
                        request.email() != null ? request.email().strip() : null,
                        request.streetAddress().strip(),
                        request.city() != null ? request.city().strip() : item.city(),
                        request.latitude(),
                        request.longitude(),
                        request.addressType() != null ? request.addressType().strip() : item.addressType(),
                        isDefault
                ));
            } else if (isDefault && item.isDefault()) {
                updated.add(new BuyerDeliveryAddressResponse(
                        item.id(),
                        item.name(),
                        item.phone(),
                        item.email(),
                        item.streetAddress(),
                        item.city(),
                        item.latitude(),
                        item.longitude(),
                        item.addressType(),
                        false
                ));
            } else {
                updated.add(item);
            }
        }

        persistAndSync(userId, updated);
        return updated.get(targetIndex);
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, String addressId) {
        List<BuyerDeliveryAddressResponse> list = readOrInitializeAddresses(userId);

        boolean removedWasDefault = false;
        List<BuyerDeliveryAddressResponse> updated = new ArrayList<>();
        for (BuyerDeliveryAddressResponse item : list) {
            if (Objects.equals(item.id(), addressId)) {
                if (item.isDefault()) {
                    removedWasDefault = true;
                }
            } else {
                updated.add(item);
            }
        }

        // If removed address was default and list is not empty, promote first address to default
        if (removedWasDefault && !updated.isEmpty()) {
            BuyerDeliveryAddressResponse first = updated.get(0);
            updated.set(0, new BuyerDeliveryAddressResponse(
                    first.id(),
                    first.name(),
                    first.phone(),
                    first.email(),
                    first.streetAddress(),
                    first.city(),
                    first.latitude(),
                    first.longitude(),
                    first.addressType(),
                    true
            ));
        }

        persistAndSync(userId, updated);
    }

    @Override
    @Transactional
    public BuyerDeliveryAddressResponse setDefaultAddress(UUID userId, String addressId) {
        List<BuyerDeliveryAddressResponse> list = readOrInitializeAddresses(userId);

        int targetIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).id(), addressId)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex == -1) {
            throw new NotFoundException("Delivery address not found: " + addressId);
        }

        List<BuyerDeliveryAddressResponse> updated = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            BuyerDeliveryAddressResponse item = list.get(i);
            boolean isTarget = (i == targetIndex);
            updated.add(new BuyerDeliveryAddressResponse(
                    item.id(),
                    item.name(),
                    item.phone(),
                    item.email(),
                    item.streetAddress(),
                    item.city(),
                    item.latitude(),
                    item.longitude(),
                    item.addressType(),
                    isTarget
            ));
        }

        persistAndSync(userId, updated);
        return updated.get(targetIndex);
    }

    @Override
    @Transactional
    public List<BuyerDeliveryAddressResponse> syncAddresses(UUID userId, List<BuyerDeliveryAddressRequest> requests) {
        List<BuyerDeliveryAddressResponse> list = new ArrayList<>();
        boolean hasDefault = false;

        for (BuyerDeliveryAddressRequest req : requests) {
            String id = (req.id() != null && !req.id().isBlank())
                    ? req.id().strip()
                    : "addr-" + UUID.randomUUID();

            boolean isDef = Boolean.TRUE.equals(req.isDefault());
            if (isDef && !hasDefault) {
                hasDefault = true;
            } else if (isDef) {
                isDef = false; // Only one default allowed
            }

            list.add(new BuyerDeliveryAddressResponse(
                    id,
                    req.name().strip(),
                    req.phone().strip(),
                    req.email() != null ? req.email().strip() : null,
                    req.streetAddress().strip(),
                    req.city() != null ? req.city().strip() : null,
                    req.latitude(),
                    req.longitude(),
                    req.addressType() != null ? req.addressType().strip() : "home",
                    isDef
            ));
        }

        // If non-empty but no address is default, make the first one default
        if (!list.isEmpty() && !hasDefault) {
            BuyerDeliveryAddressResponse first = list.get(0);
            list.set(0, new BuyerDeliveryAddressResponse(
                    first.id(),
                    first.name(),
                    first.phone(),
                    first.email(),
                    first.streetAddress(),
                    first.city(),
                    first.latitude(),
                    first.longitude(),
                    first.addressType(),
                    true
            ));
        }

        persistAndSync(userId, list);
        return list;
    }

    private List<BuyerDeliveryAddressResponse> readOrInitializeAddresses(UUID userId) {
        Optional<BuyerDeliveryAddressEntity> entityOpt = addressRepo.findById(userId);
        if (entityOpt.isPresent()) {
            String json = entityOpt.get().getAddressJson();
            if (json != null && !json.isBlank()) {
                try {
                    String trimmed = json.strip();
                    if (trimmed.startsWith("[")) {
                        return mapper.readValue(trimmed, new TypeReference<List<BuyerDeliveryAddressResponse>>() {});
                    } else if (trimmed.startsWith("{")) {
                        BuyerDeliveryAddressResponse single = mapper.readValue(trimmed, BuyerDeliveryAddressResponse.class);
                        List<BuyerDeliveryAddressResponse> wrapped = new ArrayList<>();
                        wrapped.add(single);
                        return wrapped;
                    }
                } catch (Exception ex) {
                    log.warn("Failed to parse buyer delivery address JSON for user {}: {}", userId, ex.getMessage());
                }
            }
        }

        // Fallback to profile address
        List<BuyerDeliveryAddressResponse> fallbackList = new ArrayList<>();
        profileRepo.findByUserId(userId).ifPresent(profile -> {
            String street = profile.getStreetAddress();
            if (street != null && !street.isBlank()) {
                Double lat = profile.getLatitude() != null && profile.getLatitude() != 0 ? profile.getLatitude() : 10.762622;
                Double lng = profile.getLongitude() != null && profile.getLongitude() != 0 ? profile.getLongitude() : 106.660172;
                BuyerDeliveryAddressResponse defaultAddr = new BuyerDeliveryAddressResponse(
                        "addr-" + UUID.randomUUID(),
                        profile.getFullName() != null && !profile.getFullName().isBlank() ? profile.getFullName() : "Khách Hàng",
                        profile.getPhone() != null ? profile.getPhone() : "",
                        null,
                        street.strip(),
                        profile.getProvince() != null ? profile.getProvince() : "Thành phố Hồ Chí Minh",
                        lat,
                        lng,
                        "home",
                        true
                );
                fallbackList.add(defaultAddr);
                persistAddresses(userId, fallbackList);
            }
        });

        return fallbackList;
    }

    private void persistAndSync(UUID userId, List<BuyerDeliveryAddressResponse> list) {
        persistAddresses(userId, list);

        // Sync default address to UserProfile so all devices stay updated
        list.stream().filter(BuyerDeliveryAddressResponse::isDefault).findFirst().ifPresent(def -> {
            profileRepo.findByUserId(userId).ifPresent(profile -> {
                profile.setStreetAddress(def.streetAddress());
                profile.setLatitude(def.latitude());
                profile.setLongitude(def.longitude());
                if (def.city() != null && !def.city().isBlank()) {
                    profile.setProvince(def.city());
                }
                profileRepo.save(profile);
            });
        });
    }

    private void persistAddresses(UUID userId, List<BuyerDeliveryAddressResponse> list) {
        try {
            String json = mapper.writeValueAsString(list);
            BuyerDeliveryAddressEntity entity = addressRepo.findById(userId)
                    .orElseGet(() -> new BuyerDeliveryAddressEntity(userId, json));
            entity.setAddressJson(json);
            addressRepo.save(entity);
        } catch (Exception ex) {
            log.error("Failed to persist buyer delivery addresses for user {}: {}", userId, ex.getMessage(), ex);
            throw new IllegalStateException("Could not save delivery addresses", ex);
        }
    }
}
