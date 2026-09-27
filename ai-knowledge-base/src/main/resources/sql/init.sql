-- =====================================================================
-- 知识库问答系统 - MySQL 建表脚本（启动时自动执行）
-- 表：document_meta  文档元数据
-- 表：chat_session   问答会话
-- =====================================================================

CREATE TABLE IF NOT EXISTS document_meta (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    file_name   VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    file_path   VARCHAR(500)  NOT NULL COMMENT '本地存储路径',
    file_size   BIGINT        NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    file_type   VARCHAR(20)   NOT NULL COMMENT '文件类型：pdf / txt',
    chunk_count INT           NOT NULL DEFAULT 0 COMMENT '分片数量（已写入向量库）',
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCESS/FAILED',
    error_msg   VARCHAR(1000) DEFAULT NULL COMMENT '处理失败原因',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='知识库文档元数据';

CREATE TABLE IF NOT EXISTS chat_session (
    id          VARCHAR(64)  PRIMARY KEY COMMENT '会话ID（UUID）',
    title       VARCHAR(255) NOT NULL DEFAULT '新对话' COMMENT '会话标题（默认取首轮问题）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_update_time (update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='问答会话';
