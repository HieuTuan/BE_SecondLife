package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateProposalResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplateProposal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class CategoryQuestionTemplateProposalMapper {
    private final Delegate delegate;

    public CategoryQuestionTemplateProposalMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public CategoryQuestionTemplateProposalResponse toResponse(
            CategoryQuestionTemplateProposal source,
            String categoryName,
            String itemName) {
        return delegate.toResponse(source, categoryName, itemName);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "categoryName", source = "categoryName")
        @Mapping(target = "itemName", source = "itemName")
        CategoryQuestionTemplateProposalResponse toResponse(
                CategoryQuestionTemplateProposal source,
                String categoryName,
                String itemName);
    }
}

