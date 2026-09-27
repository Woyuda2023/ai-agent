package com.example.rag.service;

import com.example.rag.dto.ChatMessageDto;
import com.example.rag.dto.SseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

/**
 * 问答核心服务：
 * 历史上下文 -> Query Rewrite -> 向量检索+Rerank -> Prompt 拼接 -> 大模型流式回答（SSE）
 * 回答完成后将本轮问答写入 Redis 会话。
 */
@Slf4j
@Service
public class ChatService {

    private final ChatModel chatModel;
    private final RetrievalService retrievalService;
    private final SessionService sessionService;
    private final QueryRewriteService queryRewriteService;

    @Value("${spring.ai.ollama.chat.options.model:qwen2.5:7b}")
    private String chatModelName;

    @Value("${spring.ai.ollama.chat.options.temperature:0.7}")
    private double temperature;

    public ChatService(ChatModel chatModel,
                       RetrievalService retrievalService,
                       SessionService sessionService,
                       QueryRewriteService queryRewriteService) {
        this.chatModel = chatModel;
        this.retrievalService = retrievalService;
        this.sessionService = sessionService;
        this.queryRewriteService = queryRewriteService;
    }

    /** 非流式问答（调试 / 简单接入用） */
    public String chat(String sessionId, String question) {
        String rewritten = queryRewriteService.rewrite(sessionId, question);
        List<Document> documents = retrievalService.retrieve(rewritten);
        List<Message> messages = buildMessages(sessionService.getMessages(sessionId), rewritten, buildContext(documents));

        String answer = chatModel.call(new Prompt(messages, ollamaOptions()))
                .getResult()
                .getOutput()
                .getText();

        sessionService.saveExchange(sessionId,
                new ChatMessageDto("user", question),
                new ChatMessageDto("assistant", answer));
        return answer;
    }

    /**
     * 流式问答：执行完整 RAG 流水线，增量文本通过 SseEmitter 推送。
     * 事件：delta（增量）/ done（含完整回答）/ error
     */
    public void chatStream(String sessionId, String question, SseEmitter emitter) {
        try {
            // 1. 历史上下文
            List<ChatMessageDto> history = sessionService.getMessages(sessionId);

            // 2. Query Rewrite：解决代词指代
            String rewritten = queryRewriteService.rewrite(sessionId, question);

            // 3. 向量检索 + Rerank
            List<Document> documents = retrievalService.retrieve(rewritten);
            String context = buildContext(documents);
            log.info("RAG 检索: 问题={}, 命中上下文数={}", rewritten, documents.size());

            // 4. 拼接 Prompt（系统约束 + 历史 + 当前问题）
            List<Message> messages = buildMessages(history, rewritten, context);

            // 5. 大模型流式生成
            StringBuilder full = new StringBuilder();
            chatModel.stream(new Prompt(messages, ollamaOptions()))
                    .subscribe(
                            response -> {
                                String text = safeText(response);
                                if (StringUtils.hasText(text)) {
                                    full.append(text);
                                    send(emitter, new SseEvent("delta", text, sessionId, null));
                                }
                            },
                            error -> {
                                log.error("流式生成失败", error);
                                send(emitter, new SseEvent("error", "生成失败: " + error.getMessage(), sessionId, null));
                                emitter.complete();
                            },
                            () -> {
                                // 6. 保存本轮问答（含自动摘要压缩）
                                sessionService.saveExchange(sessionId,
                                        new ChatMessageDto("user", question),
                                        new ChatMessageDto("assistant", full.toString()));
                                send(emitter, new SseEvent("done", null, sessionId, full.toString()));
                                emitter.complete();
                            }
                    );
        } catch (Exception e) {
            log.error("问答流程异常", e);
            send(emitter, new SseEvent("error", "系统异常: " + e.getMessage(), sessionId, null));
            emitter.complete();
        }
    }

    /** 组装模型消息：System（RAG 约束 + 历史摘要） + 历史对话 + 当前问题 */
    private List<Message> buildMessages(List<ChatMessageDto> history, String question, String context) {
        List<Message> messages = new ArrayList<>();

        StringBuilder system = new StringBuilder();
        system.append("你是一个基于私有知识库的智能问答助手，请严格依据下面的【参考资料】回答用户问题。\n");
        system.append("要求：\n");
        system.append("1. 只依据参考资料作答，不要编造参考资料中不存在的事实；\n");
        system.append("2. 如果参考资料不足以回答，请明确说明“根据现有资料无法回答”；\n");
        system.append("3. 回答使用与用户问题相同的语言；\n");
        system.append("4. 回答尽量结构化、分点清晰。\n\n");
        system.append("【参考资料】\n").append(context);
        messages.add(new SystemMessage(system.toString()));

        // 历史对话（system 摘要并入系统提示，user/assistant 正常回放）
        for (ChatMessageDto m : history) {
            if ("system".equals(m.getRole())) {
                messages.add(new SystemMessage(m.getContent()));
            } else if ("assistant".equals(m.getRole())) {
                messages.add(new AssistantMessage(m.getContent()));
            } else {
                messages.add(new UserMessage(m.getContent()));
            }
        }
        messages.add(new UserMessage(question));
        return messages;
    }

    /** 将检索结果拼接为参考资料文本 */
    private String buildContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "（未检索到相关资料）";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Document doc : documents) {
            sb.append("[资料").append(index++).append("] ").append(doc.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private OllamaOptions ollamaOptions() {
        return OllamaOptions.builder()
                .model(chatModelName)
                .temperature(temperature)
                .build();
    }

    private String safeText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private void send(SseEmitter emitter, SseEvent event) {
        try {
            emitter.send(SseEmitter.event().name("message").data(event, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.warn("SSE 发送失败: {}", e.getMessage());
        }
    }
}
