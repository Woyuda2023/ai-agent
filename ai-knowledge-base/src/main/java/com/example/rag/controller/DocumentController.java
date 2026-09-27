package com.example.rag.controller;

import com.example.rag.common.Result;
import com.example.rag.entity.DocumentMeta;
import com.example.rag.service.DocumentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理接口：上传 / 列表 / 详情 / 删除。
 */
@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** 上传文档（PDF/TXT），返回元数据，解析与向量化异步执行 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentMeta> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(documentService.upload(file));
    }

    @GetMapping("/list")
    public Result<List<DocumentMeta>> list() {
        return Result.ok(documentService.list());
    }

    @GetMapping("/{id}")
    public Result<DocumentMeta> detail(@PathVariable Long id) {
        return Result.ok(documentService.detail(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.ok();
    }
}
