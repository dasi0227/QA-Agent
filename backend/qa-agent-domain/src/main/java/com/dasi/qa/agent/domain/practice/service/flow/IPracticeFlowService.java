package com.dasi.qa.agent.domain.practice.service.flow;

import com.dasi.qa.agent.types.dto.request.practice.PracticeAbandonRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeRestartRequest;
import com.dasi.qa.agent.types.dto.request.practice.ItemSaveRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeInitRequest;
import com.dasi.qa.agent.types.dto.request.practice.ItemSubmitRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeSubmitRequest;
import com.dasi.qa.agent.types.dto.response.practice.PracticeItemResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeStateResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeDetailResponse;

public interface IPracticeFlowService {

    PracticeDetailResponse init(PracticeInitRequest request);

    PracticeStateResponse exist(String qaSetId);

    PracticeDetailResponse detail(String sessionId);

    PracticeItemResponse save(ItemSaveRequest request);

    PracticeItemResponse unknown(ItemSaveRequest request);

    PracticeItemResponse answer(ItemSubmitRequest request);

    PracticeDetailResponse submit(PracticeSubmitRequest request);

    PracticeDetailResponse restart(PracticeRestartRequest request);

    PracticeDetailResponse abandon(PracticeAbandonRequest request);
}
