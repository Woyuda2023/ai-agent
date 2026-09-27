package com.example.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 流式事件。
 * type: start / delta / done / error
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SseEvent {

    /** 事件类型 */
    private String type;

    /** delta 时：本次增量文本；error 时：错误信息 */
    private String content;

    /** 会话 ID */
    private String sessionId;

    /** done 时：完整回答 */
    private String answer;
}
