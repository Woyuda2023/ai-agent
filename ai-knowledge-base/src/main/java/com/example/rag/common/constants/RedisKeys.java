package com.example.rag.common.constants;

/**
 * Redis Key 常量。
 */
public final class RedisKeys {

    /** 会话消息列表前缀：rag:chat:{sessionId} -> JSON 数组 [{role,content}] */
    public static final String CHAT_PREFIX = "rag:chat:";

    private RedisKeys() {
    }

    public static String chatKey(String sessionId) {
        return CHAT_PREFIX + sessionId;
    }
}
