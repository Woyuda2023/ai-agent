package com.example.rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * PgVector 向量库配置：
 * - 独立 PostgreSQL 数据源（与 MySQL 业务库分离）
 * - 由 Spring AI PgVectorStore 自动创建 vector_store 表与 HNSW 索引
 * - dimensions 必须与 Embedding 模型输出维度一致
 */
@Configuration
public class VectorStoreConfig {

    @Value("${pgvector.dimensions}")
    private int dimensions;

    @Bean
    @ConfigurationProperties(prefix = "pgvector.datasource")
    public DataSource pgDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public JdbcTemplate pgJdbcTemplate(@Qualifier("pgDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public VectorStore vectorStore(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate,
                                   EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(dimensions)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .schemaName("public")
                .initializeSchema(true)      // 自动建表 + HNSW 索引
                .build();
    }
}
