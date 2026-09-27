package com.example.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.common.BusinessException;
import com.example.rag.entity.DocumentMeta;
import com.example.rag.mapper.DocumentMetaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 文档服务：上传 -> 保存本地 -> 记录元数据 -> 异步解析/分片/向量化。
 */
@Slf4j
@Service
public class DocumentService {

    private final DocumentMetaMapper documentMetaMapper;
    private final TextParserService textParserService;
    private final ChunkingService chunkingService;
    private final VectorIndexService vectorIndexService;

    @Value("${rag.upload-dir:./uploads}")
    private String uploadDir;

    private static final List<String> SUPPORTED_TYPES = List.of("pdf", "txt");

    public DocumentService(DocumentMetaMapper documentMetaMapper,
                           TextParserService textParserService,
                           ChunkingService chunkingService,
                           VectorIndexService vectorIndexService) {
        this.documentMetaMapper = documentMetaMapper;
        this.textParserService = textParserService;
        this.chunkingService = chunkingService;
        this.vectorIndexService = vectorIndexService;
    }

    /** 上传文档：立即返回元数据，解析与向量化异步执行 */
    public DocumentMeta upload(MultipartFile file) {
        String originalName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename(), "文件名不能为空"));
        String ext = getExtension(originalName);
        if (!SUPPORTED_TYPES.contains(ext)) {
            throw new BusinessException(400, "仅支持 PDF / TXT 文件");
        }
        if (file.isEmpty()) {
            throw new BusinessException(400, "文件内容为空");
        }
        try {
            File dir = new File(uploadDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BusinessException("创建上传目录失败: " + uploadDir);
            }
            String storeName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            File dest = new File(dir, storeName);
            file.transferTo(dest.toPath());

            DocumentMeta meta = new DocumentMeta();
            meta.setFileName(originalName);
            meta.setFilePath(dest.getAbsolutePath());
            meta.setFileSize(file.getSize());
            meta.setFileType(ext);
            meta.setChunkCount(0);
            meta.setStatus("PENDING");
            documentMetaMapper.insert(meta);

            asyncProcess(meta.getId());
            return documentMetaMapper.selectById(meta.getId());
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败: " + e.getMessage());
        }
    }

    /** 异步处理：解析 -> 分片 -> 向量化入库 */
    @Async("docProcessExecutor")
    public void asyncProcess(Long docId) {
        DocumentMeta meta = documentMetaMapper.selectById(docId);
        if (meta == null) {
            return;
        }
        meta.setStatus("PROCESSING");
        documentMetaMapper.updateById(meta);
        try {
            String text = textParserService.parse(meta.getFilePath(), meta.getFileType());
            if (text == null || text.isBlank()) {
                throw new BusinessException("文档内容为空，无法解析");
            }
            List<String> chunks = chunkingService.split(text);
            int count = vectorIndexService.indexChunks(meta, chunks);

            meta.setChunkCount(count);
            meta.setStatus("SUCCESS");
            meta.setErrorMsg(null);
            documentMetaMapper.updateById(meta);
            log.info("文档处理完成: id={}, chunks={}", docId, count);
        } catch (Exception e) {
            log.error("文档处理失败: id={}", docId, e);
            meta.setStatus("FAILED");
            meta.setErrorMsg(e.getMessage());
            documentMetaMapper.updateById(meta);
        }
    }

    /** 删除文档：向量 + 本地文件 + 元数据 */
    public void delete(Long id) {
        DocumentMeta meta = documentMetaMapper.selectById(id);
        if (meta == null) {
            throw new BusinessException(404, "文档不存在");
        }
        try {
            vectorIndexService.deleteByDocId(id, meta.getChunkCount());
        } catch (Exception e) {
            log.warn("删除向量失败（继续删除元数据）: {}", e.getMessage());
        }
        File file = new File(meta.getFilePath());
        if (file.exists() && file.isFile()) {
            boolean deleted = file.delete();
            log.info("删除本地文件: {} -> {}", meta.getFilePath(), deleted);
        }
        documentMetaMapper.deleteById(id);
    }

    public List<DocumentMeta> list() {
        return documentMetaMapper.selectList(
                new LambdaQueryWrapper<DocumentMeta>().orderByDesc(DocumentMeta::getCreateTime));
    }

    public DocumentMeta detail(Long id) {
        DocumentMeta meta = documentMetaMapper.selectById(id);
        if (meta == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return meta;
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx + 1).toLowerCase(Locale.ROOT) : "";
    }
}
