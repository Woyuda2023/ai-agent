# AI 知识库问答系统（RAG）

基于 **Spring AI + Ollama** 构建的 RAG 文档知识库问答后端系统：支持 PDF/TXT 文档上传解析、向量存储与语义检索，结合大模型实现基于私有文档的问答能力，解决大模型幻觉问题，支持 **SSE 流式问答**与**多轮对话**。

## 技术栈

| 组件 | 用途 |
| --- | --- |
| Spring Boot 3.4 / Java 17 | 应用框架 |
| Spring AI 1.0.0 | AI 模型调用抽象（Chat / Embedding / VectorStore） |
| Ollama（qwen2.5:7b + bge-m3 + bge-reranker-v2-m3） | 本地大模型 / Embedding / Rerank |
| PgVector（PostgreSQL 16） | 向量库，存储文档向量索引 |
| MySQL 8 | 业务元数据（文档记录、会话列表） |
| Redis 7 | 多轮对话记录（含摘要压缩） |
| MyBatis-Plus | MySQL 访问 |
| PDFBox | PDF 文本解析 |
| Docker Compose | 一键启动 PostgreSQL / MySQL / Redis |

## 核心功能

- **文档解析**：PDF / TXT 内容解析，UTF-8/GBK 自动识别，按空行切段保留段落语义
- **重叠分片**：chunk=500 字符、overlap=50 字符，句子边界断开，减少语义断裂
- **向量检索**：Embedding（bge-m3, 1024 维）写入 PgVector（HNSW + 余弦距离），语义相似度召回
- **Rerank 重排**：调用 Ollama bge-reranker-v2-m3 精排，失败自动降级原始顺序
- **SSE 流式问答**：增量推送 delta 事件，前端可打字机式展示
- **多轮对话**：Redis 存历史消息；Query Rewrite 解决代词指代；长对话自动摘要压缩防止上下文超限

## 系统架构

```
┌────────────┐  上传 PDF/TXT   ┌──────────────────────────────────────────────┐
│   前端     │ ──────────────► │              Spring Boot 应用                 │
│ Vue/React  │ ◄──SSE 流式───  │  DocumentController / ChatController         │
└────────────┘                 │        │            │            │           │
                               │        ▼            ▼            ▼           │
                               │  TextParser   Chunking    Session(Redis)    │
                               │  PDFBox/TXT   重叠分片      QueryRewrite     │
                               │        │            │       摘要压缩          │
                               │        ▼            ▼            │           │
                               │  VectorIndex ──► PgVector ◄── Retrieval      │
                               │   (Embedding)   (向量库)      +Rerank        │
                               │        ▲                        │            │
                               │        └──────── Prompt ◄───────┘            │
                               │                  │                           │
                               │                  ▼                           │
                               │            Ollama Chat(SSE)                 │
                               └──────────────────────────────────────────────┘
```

## 目录结构

```
ai-knowledge-base/
├── pom.xml
├── docker-compose.yml              # PostgreSQL(pgvector) + MySQL + Redis
├── src/main/resources/
│   ├── application.yml             # 全部可配置项
│   └── sql/init.sql                # MySQL 自动建表
└── src/main/java/com/example/rag/
    ├── RagApplication.java
    ├── config/                     # 向量库/异步/CORS/Redis 配置
    ├── common/                     # 统一返回、异常处理
    ├── controller/                 # 文档、问答、会话接口
    ├── dto/                        # 请求/事件模型
    ├── entity/  mapper/            # MySQL 实体与 Mapper
    └── service/                    # 解析、分片、向量、检索、Rerank、会话、问答
```

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6.3+
- Docker（含 Docker Compose）
- [Ollama](https://ollama.com/)（Windows 直接安装）

### 2. 启动基础设施

```bash
docker compose up -d
```

启动 PostgreSQL（pgvector 扩展）、MySQL（库 `rag_kb`）、Redis。可访问 `http://localhost:5432` 检查。

### 3. 准备 Ollama 模型

```bash
ollama pull qwen2.5:7b            # 对话大模型
ollama pull bge-m3                # Embedding 模型（1024 维）
ollama pull bge-reranker-v2-m3    # Rerank 重排序模型（Ollama 0.6+）
```

### 4. 启动应用

```bash
mvn spring-boot:run
```

启动时自动完成：MySQL 建表（`document_meta`、`chat_session`）、PgVector 建表（`vector_store` + HNSW 索引）。

### 5. 验证

```bash
curl http://localhost:8080/api/document/list
# {"code":200,"message":"success","data":[]}
```

## 接口文档

### 文档管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/document/upload` | 上传文档（multipart，字段 `file`，支持 pdf/txt，≤20MB） |
| GET | `/api/document/list` | 文档列表（含处理状态） |
| GET | `/api/document/{id}` | 文档详情 |
| DELETE | `/api/document/{id}` | 删除文档（同时删向量与本地文件） |

上传后 `status` 流转：`PENDING → PROCESSING → SUCCESS/FAILED`，轮询 `/api/document/{id}` 可查看进度与分片数。

### 会话管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/session/create` | 创建会话，返回 `id` |
| GET | `/api/session/list` | 会话列表 |
| GET | `/api/session/{id}/messages` | 会话历史消息 |
| DELETE | `/api/session/{id}` | 删除会话 |

### 问答

**SSE 流式问答（推荐）**

```bash
curl -N -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<会话ID>","question":"公司报销流程是什么？"}'
```

响应事件（`event: message`，JSON）：

```json
{"type":"delta","content":"报销流程分为三","sessionId":"xxx"}
{"type":"delta","content":"个步骤……","sessionId":"xxx"}
{"type":"done","answer":"……完整回答……","sessionId":"xxx"}
{"type":"error","message":"错误信息","sessionId":"xxx"}
```

**非流式**

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<会话ID>","question":"报销流程是什么？"}'
# {"code":200,"message":"success","data":{"answer":"..."}}
```

## 配置说明（application.yml）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Ollama 地址 |
| `spring.ai.ollama.chat.options.model` | `qwen2.5:7b` | 对话模型 |
| `spring.ai.ollama.embedding.options.model` | `bge-m3` | Embedding 模型 |
| `pgvector.dimensions` | `1024` | **必须与 embedding 模型维度一致**（nomic-embed-text 为 768） |
| `rag.chunk-size` / `rag.overlap-size` | `500` / `50` | 分片大小 / 重叠长度（字符） |
| `rag.top-k` / `rag.top-n` | `10` / `5` | 向量召回候选数 / Rerank 后保留数 |
| `rag.similarity-threshold` | `0.3` | 相似度阈值 |
| `rag.rerank.enabled` | `true` | Rerank 开关（关闭则直接取 topK 前 topN） |
| `rag.session.max-messages` / `summary-threshold` | `20` / `16` | 消息上限 / 压缩时保留的最近条数 |

## 核心实现要点

### 重叠分片（ChunkingService）

1. 按空行切段落，保留段落语义；
2. 段落合并为接近 chunkSize 的块；
3. 超长块滑动窗口切分，窗口 = chunkSize，步长 = chunkSize − overlapSize；
4. 窗口未到文末时在句子边界（。！？.；）处断开，避免从句子中间截断。

### Query Rewrite（QueryRewriteService）

首轮直接使用原问题；多轮时把历史对话 + 当前问题交给大模型，改写为不依赖上下文的独立问题，解决“它 / 这个 / 上面提到”等指代歧义；失败自动回退原问题。

### 长对话摘要压缩（SessionService）

消息超过 `max-messages`（20）时，保留最近 16 条完整消息，更早消息交给大模型生成 ≤300 字摘要，以 system 角色消息代替，防止上下文超限。

### SSE 流式（ChatService）

`SseEmitter(0L)` 不超时；`chatModel.stream()` 的 `Flux<ChatResponse>` 订阅回调中逐段发送 `delta` 事件，结束后持久化本轮问答并发送 `done`。

## 常见问题

**Q：启动报错 `relation "vector_store" does not exist`？**
确认 PostgreSQL 镜像为 `pgvector/pgvector`（自带扩展），应用配置 `spring.ai.vectorstore.pgvector.initialize-schema=true`（本工程已默认开启自动建表）。

**Q：维度不匹配报错？**
更换 Embedding 模型后必须同步修改 `pgvector.dimensions`（bge-m3=1024、nomic-embed-text=768、bge-small-zh=512），并清空 `vector_store` 表重新入库。

**Q：Rerank 报 404？**
`/api/rerank` 需要 Ollama 0.6+ 与 rerank 模型。可执行 `ollama pull bge-reranker-v2-m3`；若仍不可用，设置 `rag.rerank.enabled=false` 降级。

**Q：回答质量差 / 幻觉？**
1) 检查文档是否 `SUCCESS`（`GET /api/document/list`）；2) 调小 `rag.similarity-threshold` 召回更多候选；3) 调大 `rag.top-n` 增加上下文；4) 调小 `rag.chunk-size` 减少噪音。
