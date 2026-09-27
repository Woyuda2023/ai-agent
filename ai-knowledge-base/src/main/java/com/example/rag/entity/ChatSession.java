package com.example.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答会话（MySQL，仅存元数据；对话消息存 Redis）。
 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 会话标题，默认取首轮问题 */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
