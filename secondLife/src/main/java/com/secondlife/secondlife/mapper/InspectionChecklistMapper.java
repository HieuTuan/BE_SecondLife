package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.InspectionChecklistItemResponse;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.entity.InspectionChecklistItem;
import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InspectionChecklistMapper {
    private final Delegate delegate;

    public InspectionChecklistMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public InspectionChecklistResponse toResponse(InspectionChecklistTemplate template) {
        return delegate.toResponse(template);
    }

    public InspectionChecklistItemResponse toItemResponse(InspectionChecklistItem item) {
        return delegate.toItemResponse(item);
    }

    protected List<InspectionChecklistItemResponse> toItemResponses(List<InspectionChecklistItem> items) {
        return delegate.toItemResponses(items);
    }

    protected List<InspectionChecklistItemResponse> itemsOrEmpty(List<InspectionChecklistItem> items) {
        return delegate.itemsOrEmpty(items);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "categoryId", source = "category.id")
        @Mapping(target = "categoryName", source = "category.name")
        @Mapping(target = "items", expression = "java(itemsOrEmpty(template.getItems()))")
        InspectionChecklistResponse toResponse(InspectionChecklistTemplate template);

        InspectionChecklistItemResponse toItemResponse(InspectionChecklistItem item);

        List<InspectionChecklistItemResponse> toItemResponses(List<InspectionChecklistItem> items);

        default List<InspectionChecklistItemResponse> itemsOrEmpty(List<InspectionChecklistItem> items) {
            return items == null ? List.of() : toItemResponses(items);
        }
    }
}
