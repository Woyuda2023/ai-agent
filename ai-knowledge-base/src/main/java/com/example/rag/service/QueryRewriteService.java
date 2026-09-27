package com.example.rag.service;

import com.example.rag.dto.ChatMessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 查询改写（Query Rewrite）：
 * 结合历史对话将当前问题改写为含义完整的独立问题，解决"它/这个/上面提到"等代词指代歧义。
 * 改写失败时降级为原问题，保证问答链路可用。
 */
@Slf4j
@Service
public class QueryRewriteService {

    private final SessionService sessionService;
    private final ChatModel chatModel;

    public QueryRewriteService(SessionService sessionService, ChatModel chatModel) {
        this.sessionService = sessionService;
        this.chatModel = chatModel;
    }

    public String rewrite(String sessionId, String question) {
        List<ChatMessageDto> history = sessionService.getMessages(sessionId);
        if (history.isEmpty()) {
            return question;
        }
        String prompt = buildRewritePrompt(history, question);
        try {
            String rewritten = chatModel.call(prompt);
            if (StringUtils.hasText(rewritten)) {
                String cleaned = rewritten.trim();
                // 去掉模型可能输出的引号包裹
                if (cleaned.length() >= 2 && (cleaned.startsWith("\"") && cleaned.endsWith("\"")
                        || cleaned.startsWith("“") && cleaned.endsWith("”"))) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                }
                log.info("Query Rewrite: {} -> {}", question, cleaned);
                return cleaned;
            }
        } catch (Exception e) {
            log.warn("Query Rewrite 失败，使用原问题: {}", e.getMessage());
        }
        return question;
    }

    private String buildRewritePrompt(List<ChatMessageDto> history, String question) {
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (ChatMessageDto m : history) {
            if ("system".equals(m.getRole())) {
                continue;
            }
            sb.append(idx++).append(". ").append(m.getRole()).append("：").append(m.getContent()).append('\n');
        }
        return """
                你是对话查询改写助手。请结合历史对话，把用户当前问题改写为一条不依赖上下文、含义完整的独立问题。
                如果当前问题本身已经完整明确，直接原样输出。只输出改写后的问题，不要任何解释或标点符号包装。

                【历史对话】
                %s

                【当前问题】%s

                【改写后的问题】
                """.formatted(sb, question);
    }
}
