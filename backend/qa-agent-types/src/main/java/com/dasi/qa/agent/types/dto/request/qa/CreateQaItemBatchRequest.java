package com.dasi.qa.agent.types.dto.request.qa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQaItemBatchRequest {

    @NotBlank
    private String qaSetId;

    @NotEmpty
    private List<String> questions;
}
