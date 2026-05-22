package com.dasi.qa.agent.domain.qa.service.convert;

import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ConvertException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Component
public class QaSetConverter {

    private static final String APP_NAME = "QA_Agent";
    private static final List<String> DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD");
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SCHEMA_VERSION = 1;
    private static final int QA_SET_TITLE_MAX_LENGTH = 255;
    private static final int QA_ITEM_MODULE_TAG_MAX_LENGTH = 120;

    private final IJsonUtil jsonUtil;

    public QaSetConverter(IJsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    public byte[] exportContent(QaSetResponse qaSet, List<QaItemResponse> items) {
        QaSetExportFile exportFile = QaSetExportFile.builder()
                .schemaVersion(SCHEMA_VERSION)
                .app(APP_NAME)
                .exportedAt(LocalDateTime.now().format(EXPORT_TIME_FORMATTER))
                .qaQaSetMetaInfo(QaSetExportFile.QaSetMetaInfo.builder()
                        .title(qaSet.getTitle())
                        .description(qaSet.getDescription())
                        .moduleTags(parseModuleTags(qaSet.getModuleTagsJson()))
                        .build())
                .qaSetEntries(items.stream().map(this::toEntry).toList())
                .build();
        return jsonUtil.toJsonString(exportFile).getBytes(StandardCharsets.UTF_8);
    }

    public QaSetExportFile importContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ConvertException(ResultCode.QA_SET_FILE_INVALID);
        }
        try {
            QaSetExportFile exportFile = jsonUtil.parseJsonObject(new String(content, StandardCharsets.UTF_8), QaSetExportFile.class);
            validate(exportFile);
            return exportFile;
        } catch (ConvertException e) {
            throw e;
        } catch (Exception e) {
            throw new ConvertException(ResultCode.QA_SET_FILE_INVALID);
        }
    }

    public String buildFileName(String title) {
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : "qa-set";
        String safeTitle = normalizedTitle.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        return safeTitle + ".dasi";
    }

    private QaSetExportFile.QaSetEntry toEntry(QaItemResponse item) {
        return QaSetExportFile.QaSetEntry.builder()
                .question(item.getQuestion())
                .answer(item.getAnswer())
                .knowledgeNote(item.getKnowledgeNote())
                .moduleTag(item.getModuleTag())
                .difficulty(item.getDifficulty())
                .keywords(item.getKeywords())
                .hint(item.getHint())
                .sourceReliable(item.getSourceReliable())
                .sortOrder(item.getSortOrder())
                .build();
    }

    private List<String> parseModuleTags(String moduleTagsJson) {
        if (!StringUtils.hasText(moduleTagsJson)) {
            return List.of();
        }
        try {
            return jsonUtil.parseJsonArray(moduleTagsJson, String.class);
        } catch (Exception e) {
            return Arrays.stream(moduleTagsJson.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
    }

    private void validate(QaSetExportFile exportFile) {
        if (exportFile == null
                || !Integer.valueOf(SCHEMA_VERSION).equals(exportFile.getSchemaVersion())
                || !APP_NAME.equals(exportFile.getApp())
                || exportFile.getQaQaSetMetaInfo() == null
                || !StringUtils.hasText(exportFile.getQaQaSetMetaInfo().getTitle())
                || exportFile.getQaQaSetMetaInfo().getTitle().length() > QA_SET_TITLE_MAX_LENGTH
                || exportFile.getQaSetEntries() == null
                || exportFile.getQaSetEntries().isEmpty()) {
            throw new ConvertException(ResultCode.QA_SET_FILE_INVALID);
        }
        for (QaSetExportFile.QaSetEntry qaSetEntry : exportFile.getQaSetEntries()) {
            validateEntry(qaSetEntry);
        }
    }

    private void validateEntry(QaSetExportFile.QaSetEntry qaSetEntry) {
        if (qaSetEntry == null || !StringUtils.hasText(qaSetEntry.getQuestion())) {
            throw new ConvertException(ResultCode.QA_SET_FILE_INVALID);
        }
        if (StringUtils.hasText(qaSetEntry.getDifficulty()) && !DIFFICULTIES.contains(qaSetEntry.getDifficulty())) {
            throw new ConvertException(ResultCode.QA_SET_FILE_INVALID);
        }
        if (StringUtils.hasText(qaSetEntry.getModuleTag()) && qaSetEntry.getModuleTag().length() > QA_ITEM_MODULE_TAG_MAX_LENGTH) {
            throw new ConvertException(ResultCode.QA_SET_FILE_INVALID);
        }
    }

}
