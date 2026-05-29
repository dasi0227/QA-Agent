package com.dasi.qa.agent.types.dto.request.qa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QaSetReindexRequest {

    @NotBlank
    private String qaSetId;

    @Size(max = 3)
    private List<String> documentIds;
}
