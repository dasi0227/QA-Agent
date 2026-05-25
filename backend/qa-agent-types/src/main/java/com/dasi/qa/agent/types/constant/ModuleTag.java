package com.dasi.qa.agent.types.constant;

import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@NoArgsConstructor
public class ModuleTag {

    private static final List<String> VALUES = List.of(
            "JavaSE", "OOP", "JVM", "IO", "JUC", "JCF", "MCP", "SKILL", "AGENT", "Harness",
            "SpringAI", "LangChain4J", "SpringFramework", "SpringMVC", "SpringBoot", "SpringCloud",
            "MyBatis", "MySQL", "PostgreSQL", "Redis", "MQ", "Linux", "Docker", "Maven", "Git",
            "Zookeeper", "Elasticsearch", "K8s", "Grafana", "分布式", "高并发", "微服务", "设计模式",
            "数据结构与算法", "计算机网络", "操作系统", "测试", "运维", "安全"
    );

    private static final Set<String> VALUE_SET = Set.copyOf(VALUES);

    public static boolean contains(String value) {
        return value != null && !value.isBlank() && VALUE_SET.contains(value.trim());
    }

    public static List<String> values() {
        return VALUES;
    }
}
