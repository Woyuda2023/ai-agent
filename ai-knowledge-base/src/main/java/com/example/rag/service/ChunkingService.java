package com.example.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文档重叠分片策略：
 * 1. 按空行切分为段落，保留段落语义；
 * 2. 段落合并为接近 chunkSize 的块；
 * 3. 超长块按滑动窗口切分，相邻块重叠 overlapSize 字符，尽量在句子边界断开，减少语义断裂。
 */
@Slf4j
@Service
public class ChunkingService {

    @Value("${rag.chunk-size:500}")
    private int chunkSize;

    @Value("${rag.overlap-size:50}")
    private int overlapSize;

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> paragraphs = splitParagraphs(text);
        List<String> merged = mergeParagraphs(paragraphs);

        List<String> chunks = new ArrayList<>();
        for (String block : merged) {
            if (block.length() <= chunkSize) {
                chunks.add(block.trim());
            } else {
                chunks.addAll(slideSplit(block));
            }
        }
        return chunks.stream().filter(c -> !c.isBlank()).toList();
    }

    /** 按空行切段落 */
    private List<String> splitParagraphs(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 段落合并为接近 chunkSize 的块 */
    private List<String> mergeParagraphs(List<String> paragraphs) {
        List<String> merged = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (builder.length() > 0 && builder.length() + paragraph.length() > chunkSize) {
                merged.add(builder.toString().trim());
                builder.setLength(0);
            }
            builder.append(paragraph).append('\n');
        }
        if (builder.length() > 0) {
            merged.add(builder.toString().trim());
        }
        return merged;
    }

    /** 滑动窗口重叠切分：窗口 = chunkSize，步长 = chunkSize - overlapSize */
    private List<String> slideSplit(String text) {
        List<String> result = new ArrayList<>();
        int step = Math.max(chunkSize - overlapSize, 1);
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end);

            // 窗口未到文末时，尝试在句子边界处断开，避免从句子中间截断
            if (end < text.length()) {
                int cut = lastSentenceBoundary(chunk);
                if (cut > chunkSize / 2) {
                    chunk = chunk.substring(0, cut);
                    end = start + cut;
                }
            }
            result.add(chunk.trim());

            if (end >= text.length()) {
                break;
            }
            start = end - overlapSize;
        }
        return result;
    }

    /** 返回最后一个句子结束符（含其后位置）的下标 + 1；找不到返回 -1 */
    private int lastSentenceBoundary(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == '\n' || c == '；' || c == ';') {
                return i + 1;
            }
        }
        return -1;
    }
}
