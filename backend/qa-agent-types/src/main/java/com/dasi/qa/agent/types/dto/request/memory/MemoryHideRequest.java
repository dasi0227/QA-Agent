package com.dasi.qa.agent.types.dto.request.memory;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryHideRequest {

    @NotBlank(message = "memoryId 不能为空")
    private String memoryId;
}
