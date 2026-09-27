package com.example.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 问答请求体。
 */
@Data
public class ChatRequest {

    /** 用户问题 */
    @NotBlank(message = "问题不能为空")
    private String question;

    /** 会话 ID */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;
}
