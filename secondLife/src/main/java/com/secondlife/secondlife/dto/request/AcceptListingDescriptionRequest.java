package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Size;

public record AcceptListingDescriptionRequest(@Size(max = 10000) String description) { }
