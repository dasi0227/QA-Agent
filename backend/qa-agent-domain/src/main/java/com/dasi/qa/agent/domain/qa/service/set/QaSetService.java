package com.dasi.qa.agent.domain.qa.service.set;

import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QaSetService implements IQaSetService {

    private final IQaRepository repository;
    private final IContextUtil contextUtil;

    public QaSetService(IQaRepository repository, IContextUtil contextUtil) {
        this.repository = repository;
        this.contextUtil = contextUtil;
    }

    @Override
    public QaSetResponse detailQaSet(String id) {
        return repository.detailQaSet(id, contextUtil.getUserId());
    }

    @Override
    public List<QaSetResponse> queryQaSet(QaSetRequest request) {
        return repository.queryQaSet(request, contextUtil.getUserId());
    }

    @Override
    public QaSetResponse updateQaSet(QaSetRequest request) {
        return repository.updateQaSet(request, contextUtil.getUserId());
    }

    @Override
    public void deleteQaSet(String id) {
        repository.deleteQaSet(id, contextUtil.getUserId());
    }

}
