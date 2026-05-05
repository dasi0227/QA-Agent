package com.dasi.qa.agent.infrastructure.util;

import com.aliyun.oss.OSS;
import com.dasi.qa.agent.domain.util.IOssUtil;
import com.dasi.qa.agent.infrastructure.properties.AliOssProperties;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class OssUtil implements IOssUtil {

    private final OSS ossClient;
    private final AliOssProperties aliOssProperties;

    public OssUtil(OSS ossClient, AliOssProperties aliOssProperties) {
        this.ossClient = ossClient;
        this.aliOssProperties = aliOssProperties;
    }

    @Override
    public void upload(byte[] bytes, String objectKey) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            ossClient.putObject(aliOssProperties.getBucketName(), objectKey, inputStream);
        } catch (Exception e) {
            throw new ApiException(ResultCode.INTERNAL_ERROR);
        }
    }

    @Override
    public String getPublicUrl(String uri) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            return uri;
        }
        return "https://" + aliOssProperties.getBucketName() + "." + aliOssProperties.getEndpoint() + "/" + uri;
    }

    @Override
    public void delete(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            return;
        }
        try {
            ossClient.deleteObject(aliOssProperties.getBucketName(), uri);
        } catch (Exception ignored) {
            // best-effort: object may not exist
        }
    }
}
