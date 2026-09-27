package com.example.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 基于 Spring AI + Ollama 的 RAG 文档知识库问答系统。
 * 排除 PgVectorStoreAutoConfiguration：向量库由 VectorStoreConfig 手动装配（双数据源）。
 */
@EnableAsync
@MapperScan("com.example.rag.mapper")
@SpringBootApplication(exclude = PgVectorStoreAutoConfiguration.class)
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
