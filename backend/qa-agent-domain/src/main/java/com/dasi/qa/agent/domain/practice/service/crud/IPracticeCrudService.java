package com.dasi.qa.agent.domain.practice.service.crud;

import com.dasi.qa.agent.types.dto.request.practice.PracticeQueryRequest;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;

import java.util.List;

public interface IPracticeCrudService {

    List<PracticeSessionResponse> query(PracticeQueryRequest request);
}
