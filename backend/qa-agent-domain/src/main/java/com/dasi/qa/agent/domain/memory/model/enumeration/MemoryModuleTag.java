package com.dasi.qa.agent.domain.memory.model.enumeration;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

public final class MemoryModuleTag {

    private static final List<String> VALUES = List.of(
            "JavaSE", "OOP", "JVM", "IO", "JUC", "JCF", "MCP", "SKILL", "AGENT", "Harness",
            "SpringAI", "LangChain4J", "SpringFramework", "SpringMVC", "SpringBoot", "SpringCloud",
            "MyBatis", "MySQL", "PostgreSQL", "Redis", "MQ", "Linux", "Docker", "Maven", "Git",
            "Zookeeper", "Elasticsearch", "K8s", "Grafana", "分布式", "高并发", "微服务", "设计模式",
            "数据结构与算法", "计算机网络", "操作系统", "测试", "运维", "安全"
    );

    private static final Set<String> VALUE_SET = Set.copyOf(VALUES);

    private MemoryModuleTag() {
    }

    public static boolean contains(String value) {
        return StringUtils.hasText(value) && VALUE_SET.contains(value.trim());
    }

    public static List<String> values() {
        return VALUES;
    }
}
