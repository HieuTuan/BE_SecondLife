package com.secondlife.secondlife.entity;

import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.stream.Stream;

/** Internal routing metadata. Only the invalidation is serialized to clients. */
public record ResourceChanged(String type, UUID id, List<UUID> owners) {
    public static ResourceChanged owned(String type, UUID id, User... users) {
        var owners = Stream.of(users).filter(Objects::nonNull).map(User::getId)
                .filter(Objects::nonNull).distinct().toList();
        return new ResourceChanged(type, id, owners);
    }
}
