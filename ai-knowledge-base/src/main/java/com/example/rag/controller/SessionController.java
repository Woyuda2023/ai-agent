package com.example.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.common.Result;
import com.example.rag.dto.ChatMessageDto;
import com.example.rag.entity.ChatSession;
import com.example.rag.mapper.ChatSessionMapper;
import com.example.rag.service.SessionService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话管理接口：创建 / 列表 / 消息 / 删除。
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessionService;
    private final ChatSessionMapper chatSessionMapper;

    public SessionController(SessionService sessionService, ChatSessionMapper chatSessionMapper) {
        this.sessionService = sessionService;
        this.chatSessionMapper = chatSessionMapper;
    }

    /** 创建会话，返回会话 ID（对话消息存 Redis） */
    @PostMapping("/create")
    public Result<ChatSession> create(@RequestBody(required = false) Map<String, String> body) {
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID().toString().replace("-", ""));
        String title = body == null ? null : body.get("title");
        session.setTitle(StringUtils.hasText(title) ? title : "新对话");
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return Result.ok(session);
    }

    @GetMapping("/list")
    public Result<List<ChatSession>> list() {
        return Result.ok(chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>().orderByDesc(ChatSession::getUpdateTime)));
    }

    /** 查看某会话的历史消息（含摘要消息） */
    @GetMapping("/{id}/messages")
    public Result<List<ChatMessageDto>> messages(@PathVariable String id) {
        return Result.ok(sessionService.getMessages(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        sessionService.delete(id);
        chatSessionMapper.deleteById(id);
        return Result.ok();
    }
}
