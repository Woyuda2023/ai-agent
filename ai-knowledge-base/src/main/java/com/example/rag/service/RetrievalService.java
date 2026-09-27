package com.example.rag.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索服务：向量相似度召回候选 -> Rerank 重排 -> TopN 上下文。
 */
@Service
public class RetrievalService {

    private final VectorStore vectorStore;
    private final RerankService rerankService;

    @Value("${rag.top-k:10}")
    private int topK;

    @Value("${rag.top-n:5}")
    private int topN;

    @Value("${rag.similarity-threshold:0.3}")
    private double similarityThreshold;

    public RetrievalService(VectorStore vectorStore, RerankService rerankService) {
        this.vectorStore = vectorStore;
        this.rerankService = rerankService;
    }

    public List<Document> retrieve(String query) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        List<Document> candidates = vectorStore.similaritySearch(searchRequest);
        return rerankService.rerank(query, candidates, topN);
    }
}
