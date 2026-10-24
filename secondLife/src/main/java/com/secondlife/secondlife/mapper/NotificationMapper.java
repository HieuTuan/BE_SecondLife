package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.NotificationResponseDTO;
import com.secondlife.secondlife.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {
    private final Delegate delegate;

    public NotificationMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public NotificationResponseDTO toResponse(Notification notification) {
        return delegate.toResponse(notification);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "isRead", source = "read")
        NotificationResponseDTO toResponse(Notification notification);
    }
}
