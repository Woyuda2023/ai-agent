package com.example.rag.controller;

import com.example.rag.common.Result;
import com.example.rag.dto.ChatRequest;
import com.example.rag.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 问答接口：
 * - POST /api/chat/stream  SSE 流式问答（推荐，前端可用 EventSource / fetch + ReadableStream 消费）
 * - POST /api/chat         非流式问答（调试用）
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        // 0L 表示不超时，连接保持到流式回答结束
        SseEmitter emitter = new SseEmitter(0L);
        chatService.chatStream(request.getSessionId(), request.getQuestion(), emitter);
        return emitter;
    }

    @PostMapping
    public Result<Map<String, String>> chat(@Valid @RequestBody ChatRequest request) {
        String answer = chatService.chat(request.getSessionId(), request.getQuestion());
        return Result.ok(Map.of("answer", answer));
    }
}
