package com.example.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档元数据（MySQL）。
 */
@Data
@TableName("document_meta")
public class DocumentMeta {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 本地存储路径 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件类型：pdf / txt */
    private String fileType;

    /** 分片数量（已写入向量库） */
    private Integer chunkCount;

    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String status;

    /** 处理失败原因 */
    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
