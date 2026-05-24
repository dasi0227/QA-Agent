package com.dasi.qa.agent.domain.qa.service.set;

import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.domain.qa.service.convert.QaSetConverter;
import com.dasi.qa.agent.domain.qa.service.convert.QaSetExportFile;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.types.dto.request.qa.CreateEmptyQaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetImportRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetExportResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ConvertException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class QaSetService implements IQaSetService {

    private final IQaRepository repository;
    private final IContextUtil contextUtil;
    private final QaSetConverter converter;
    private final IIdUtil idUtil;

    public QaSetService(IQaRepository repository,
                        IContextUtil contextUtil,
                        QaSetConverter converter,
                        IIdUtil idUtil) {
        this.repository = repository;
        this.contextUtil = contextUtil;
        this.converter = converter;
        this.idUtil = idUtil;
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
    public QaSetResponse createEmptyQaSet(CreateEmptyQaSetRequest request) {
        return repository.createEmptyQaSet(idUtil.nextId(), request, contextUtil.getUserId());
    }

    @Override
    public QaSetResponse updateQaSet(QaSetRequest request) {
        return repository.updateQaSet(request, contextUtil.getUserId());
    }

    @Override
    public void deleteQaSet(String id) {
        repository.deleteQaSet(id, contextUtil.getUserId());
    }

    @Override
    public QaSetExportResponse exportQaSet(String id) {
        String userId = contextUtil.getUserId();
        QaSetResponse qaSet = repository.detailQaSet(id, userId);
        List<QaItemResponse> items = repository.queryQaItemsBySetId(id, userId);
        return QaSetExportResponse.builder()
                .fileName(converter.buildFileName(qaSet.getTitle()))
                .content(converter.exportContent(qaSet, items))
                .build();
    }

    @Override
    public QaSetResponse importQaSet(QaSetImportRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getFileName())
                || !request.getFileName().toLowerCase().endsWith(".dasi")
                || request.getContent() == null
                || request.getContent().length == 0) {
            throw new ConvertException(ResultCode.QA_SET_FILE_INVALID);
        }
        QaSetExportFile exportFile = converter.importContent(request.getContent());
        return repository.importQaSet(exportFile, contextUtil.getUserId());
    }

}
