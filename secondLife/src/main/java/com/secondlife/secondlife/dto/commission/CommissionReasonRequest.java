package com.secondlife.secondlife.dto.commission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CommissionReasonRequest(@NotBlank(message = "reason is required")
        @Size(max = 1000, message = "reason must not exceed 1000 characters")
        @JsonDeserialize(using = CommissionJsonTypes.StringValue.class)
        @Schema(example = "Disable policy for new orders") String reason) {
    @JsonAnySetter
    public void rejectUnsupportedField(String field, Object value) {
        throw new com.secondlife.secondlife.exception.InvalidRequestFieldException(field,
                "Unsupported field; only reason is supported");
    }
}
