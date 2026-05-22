package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedQaSetSaveResult {

    private String qaSetId;

    private List<String> qaItemIds;
}
