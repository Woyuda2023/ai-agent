package com.example.rag.service;

import com.example.rag.entity.DocumentMeta;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量索引服务：将分片文本转为 Document 并写入 PgVector（内部自动调用 Embedding 模型）。
 * chunkId 规则：{docId}_{index}，删除时按规则重建。
 */
@Service
public class VectorIndexService {

    private final VectorStore vectorStore;

    public VectorIndexService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 索引一个文档的全部分片，返回写入数量 */
    public int indexChunks(DocumentMeta meta, List<String> chunks) {
        List<Document> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("docId", String.valueOf(meta.getId()));
            metadata.put("fileName", meta.getFileName());
            metadata.put("chunkIndex", i);
            documents.add(new Document(chunkId(meta.getId(), i), chunks.get(i), metadata));
        }
        vectorStore.add(documents);
        return documents.size();
    }

    /** 删除指定文档的全部向量 */
    public void deleteByDocId(Long docId, int chunkCount) {
        List<String> ids = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            ids.add(chunkId(docId, i));
        }
        vectorStore.delete(ids);
    }

    private String chunkId(Long docId, int index) {
        return docId + "_" + index;
    }
}
