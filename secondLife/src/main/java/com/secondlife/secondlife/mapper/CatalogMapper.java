package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.dto.response.ItemResponse;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.Item;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class CatalogMapper {
    private final Delegate delegate;

    public CatalogMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public CategoryResponse toCategoryResponse(Category category) {
        return delegate.toCategoryResponse(category);
    }

    public ItemResponse toItemResponse(Item item) {
        return delegate.toItemResponse(item);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        CategoryResponse toCategoryResponse(Category category);

        @Mapping(target = "categoryId", source = "category.id")
        ItemResponse toItemResponse(Item item);
    }
}
