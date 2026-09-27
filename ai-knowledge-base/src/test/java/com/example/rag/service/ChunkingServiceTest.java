package com.example.rag.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 重叠分片策略单元测试。
 */
class ChunkingServiceTest {

    private ChunkingService newService(int chunkSize, int overlap) {
        ChunkingService service = new ChunkingService();
        ReflectionTestUtils.setField(service, "chunkSize", chunkSize);
        ReflectionTestUtils.setField(service, "overlapSize", overlap);
        return service;
    }

    @Test
    void 短文本_分片数为1() {
        ChunkingService service = newService(500, 50);
        List<String> chunks = service.split("这是一段很短的文档内容。");
        assertEquals(1, chunks.size());
        assertEquals("这是一段很短的文档内容。", chunks.get(0));
    }

    @Test
    void 长文本_多分片_相邻块存在重叠() {
        ChunkingService service = newService(500, 50);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("第").append(i).append("段内容用于测试重叠分片是否生效。");
        }
        List<String> chunks = service.split(sb.toString());
        assertTrue(chunks.size() >= 2, "长文本应产生多个分片");

        // 相邻块之间应有 overlap 长度的重叠内容
        for (int i = 0; i < chunks.size() - 1; i++) {
            String head = chunks.get(i);
            String next = chunks.get(i + 1);
            if (head.length() > 10 && next.length() > 10) {
                assertTrue(sharedTail(head, next) > 0, "相邻分片应存在重叠: i=" + i);
            }
        }
    }

    @Test
    void 每块长度不超过chunkSize_且非空() {
        ChunkingService service = newService(500, 50);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("测试文本块的内容重复填充，验证滑动窗口长度约束，同时补充更多字符。");
        }
        List<String> chunks = service.split(sb.toString());
        assertFalse(chunks.isEmpty());
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 500, "分片长度超限: " + chunk.length());
            assertFalse(chunk.isBlank());
        }
    }

    @Test
    void 空文本与空白文本_返回空列表() {
        ChunkingService service = newService(500, 50);
        assertTrue(service.split("").isEmpty());
        assertTrue(service.split("   \n\n  ").isEmpty());
        assertTrue(service.split(null).isEmpty());
    }

    /** 统计 head 尾部与 next 头部的最大公共重叠长度 */
    private int sharedTail(String head, String next) {
        int max = Math.min(head.length(), next.length());
        int len = 0;
        for (int i = 1; i <= Math.min(max, 100); i++) {
            if (head.substring(head.length() - i).equals(next.substring(0, i))) {
                len = i;
            }
        }
        return len;
    }
}
