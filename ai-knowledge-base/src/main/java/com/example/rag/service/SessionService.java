package com.example.rag.service;

import com.example.rag.common.constants.RedisKeys;
import com.example.rag.dto.ChatMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理（Redis）：
 * - 多轮对话消息以 JSON 数组存储于 rag:chat:{sessionId}
 * - 消息超过 max-messages 时，将最旧部分压缩为摘要（长对话摘要压缩）
 */
@Slf4j
@Service
public class SessionService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;

    @Value("${rag.session.max-messages:20}")
    private int maxMessages;

    @Value("${rag.session.summary-threshold:16}")
    private int summaryThreshold;

    @Value("${spring.ai.ollama.chat.options.model:qwen2.5:7b}")
    private String chatModelName;

    public SessionService(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          ChatModel chatModel) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
    }

    /** 读取会话全部消息（含摘要消息） */
    public List<ChatMessageDto> getMessages(String sessionId) {
        String json = redisTemplate.opsForValue().get(RedisKeys.chatKey(sessionId));
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChatMessageDto>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("会话消息反序列化失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /** 保存一轮问答，超限自动压缩 */
    public void saveExchange(String sessionId, ChatMessageDto userMsg, ChatMessageDto assistantMsg) {
        List<ChatMessageDto> messages = getMessages(sessionId);
        messages.add(userMsg);
        messages.add(assistantMsg);
        if (messages.size() > maxMessages) {
            messages = compress(messages);
        }
        saveMessages(sessionId, messages);
    }

    public void delete(String sessionId) {
        redisTemplate.delete(RedisKeys.chatKey(sessionId));
    }

    private void saveMessages(String sessionId, List<ChatMessageDto> messages) {
        try {
            redisTemplate.opsForValue().set(RedisKeys.chatKey(sessionId), objectMapper.writeValueAsString(messages));
        } catch (JsonProcessingException e) {
            log.error("会话消息序列化失败: {}", e.getMessage());
        }
    }

    /**
     * 长对话摘要压缩：
     * 保留最近 summary-threshold 条完整消息，更早的消息交给 LLM 生成摘要，
     * 以 system 角色摘要消息替代，控制上下文长度、防止超限。
     */
    private List<ChatMessageDto> compress(List<ChatMessageDto> messages) {
        int keep = Math.min(summaryThreshold, messages.size());
        List<ChatMessageDto> old = new ArrayList<>(messages.subList(0, messages.size() - keep));
        List<ChatMessageDto> recent = new ArrayList<>(messages.subList(messages.size() - keep, messages.size()));

        String summary = summarize(old);
        List<ChatMessageDto> result = new ArrayList<>();
        if (StringUtils.hasText(summary)) {
            result.add(new ChatMessageDto("system", "以下是更早对话的摘要，回答时需结合该摘要与当前对话：" + summary));
        }
        result.addAll(recent);
        log.info("会话消息超过上限，已压缩: 摘要={}字符, 保留最近{}条", summary == null ? 0 : summary.length(), recent.size());
        return result;
    }

    private String summarize(List<ChatMessageDto> messages) {
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (ChatMessageDto m : messages) {
            sb.append(idx++).append(". ").append(m.getRole()).append("：").append(m.getContent()).append('\n');
        }
        String prompt = """
                请将以下多轮对话压缩为一段简洁的中文摘要，保留关键问题、回答要点与已确定的事实，不超过 300 字，只输出摘要内容：

                %s
                """.formatted(sb);
        try {
            return chatModel.call(prompt).trim();
        } catch (Exception e) {
            log.warn("对话摘要生成失败: {}", e.getMessage());
            return null;
        }
    }
}
