package com.example.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息（Redis 存储单元 + SSE 消息体）。
 * role: user / assistant / system（摘要消息）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private String role;

    private String content;
}
