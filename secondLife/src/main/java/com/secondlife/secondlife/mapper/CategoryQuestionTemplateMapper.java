package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

@Component
public class CategoryQuestionTemplateMapper {
    private final Delegate delegate;

    public CategoryQuestionTemplateMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public CategoryQuestionTemplateResponse toResponse(CategoryQuestionTemplate source) {
        return delegate.toResponse(source);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        CategoryQuestionTemplateResponse toResponse(CategoryQuestionTemplate source);
    }
}
