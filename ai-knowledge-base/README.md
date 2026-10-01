# AI 知识库问答系统（RAG）

基于 **Spring Boot + Spring AI** 构建的 RAG 文档知识库问答后端系统：支持 PDF/TXT 文档上传解析、调用在线 Embedding API 将文本向量化并**存入 MySQL 向量字段**，通过向量相似度检索 + 大模型 API 实现基于私有文档的问答，解决大模型幻觉问题，支持 **SSE 流式问答**与**多轮对话**。**默认使用智谱 BigModel（对话 `glm-4.7-flash` + 向量 `embedding-3`，API Key 通过环境变量 `LLM_API_KEY` 配置）**，亦可切换通义千问 / 其他 OpenAI 兼容服务商（改 4 个配置项即可）。

## 技术栈

| 组件 | 用途 |
| --- | --- |
| Spring Boot 3.4 / Java 17 | 应用框架 |
| Spring AI 1.0.0 | AI 模型调用抽象（Chat / Embedding） |
| 在线大模型 API（OpenAI 兼容） | 对话 + Embedding（默认智谱 BigModel，Key 由环境变量 `LLM_API_KEY` 注入；可切换通义 DashScope 等） |
| MySQL 8 | 唯一数据库：文档元数据 / **向量字段** / 会话消息 |
| MyBatis（MyBatis-Plus） | MySQL 数据访问 |
| PDFBox | PDF 文本解析 |
| Docker Compose | 一键启动 MySQL |

## 核心功能

- **文档解析**：PDF / TXT 内容解析，UTF-8/GBK 自动识别，按空行切段保留段落语义
- **重叠分片**：chunk=500 字符、overlap=50 字符，句子边界断开，减少语义断裂
- **向量存储（MySQL）**：调用在线 Embedding API（默认智谱 `embedding-3`）生成 1024 维向量，归一化后以 float32 数组写入 `document_chunk.embedding`（BLOB 字段），构建文档向量索引
- **检索问答**：问题向量化后应用层计算余弦相似度（归一化向量点积），按阈值过滤、TopK 召回，拼接上下文 Prompt 调用大模型生成答案
- **SSE 流式问答**：增量推送 delta 事件，前端打字机式展示
- **多轮对话**：历史消息持久化于 MySQL；Query Rewrite 解决代词指代；长对话自动摘要压缩防止上下文超限
- **业务容错**：文件格式/大小校验；API 调用超时、限流（429）、网络异常分类捕获；全局异常处理器返回友好提示

## 系统架构

```
┌────────────┐  上传 PDF/TXT   ┌───────────────────────────────────────────────┐
│   前端     │ ──────────────► │               Spring Boot 应用                 │
│ (Vue3)     │ ◄──SSE 流式───  │  DocumentController / ChatController          │
└────────────┘                 │        │            │            │            │
                               │        ▼            ▼            ▼            │
                               │  TextParser   Chunking     Session(MySQL)     │
                               │  PDFBox/TXT   重叠分片      QueryRewrite       │
                               │        │            │        摘要压缩          │
                               │        ▼            ▼            │            │
                               │  MysqlVector ◄── Embedding ─────┤            │
                               │   (余弦相似度)     在线 API      │            │
                               │        ▲            │            │            │
                               │        │            ▼            │            │
                               │        └──────── Prompt ◄───────┘            │
                               │                   │                          │
                               │                   ▼                          │
                               │       在线 LLM API（OpenAI兼容,SSE,默认智谱） │
                               └───────────────────────────────────────────────┘
```

向量存储不依赖独立向量数据库：文档向量与业务数据同库（MySQL），检索在应用层完成余弦相似度计算，部署简单（单数据库即可）。

## 目录结构

```
ai-knowledge-base/
├── pom.xml
├── docker-compose.yml              # 一键启动 MySQL
├── src/main/resources/
│   ├── application.yml             # 全部可配置项（模型 API / RAG 参数）
│   └── sql/init.sql                # MySQL 自动建表（document_meta / document_chunk / chat_session / chat_message）
├── src/main/java/com/example/rag/  # 后端源码
└── frontend/                       # 前端工程（Vue3 + Vite）
    ├── package.json
    ├── vite.config.js              # 开发代理 /api → localhost:8080
    ├── copy-dist.mjs               # 生产部署脚本（构建产物 → 后端 static）
    └── src/
        ├── App.vue                 # 布局 + 会话状态
        ├── api/index.js            # REST / SSE 封装（原生 fetch）
        ├── utils/toast.js
        └── components/             # DocumentPanel / SessionPanel / ChatPanel
```

## 快速开始

### 1. 环境要求

- JDK 17+ / Maven 3.6.3+
- MySQL 8（本机安装或 `docker compose up -d`）
- **模型 Key 由环境变量注入**：默认智谱 BigModel（对话 `glm-4.7-flash` 免费 + 向量 `embedding-3`），启动前设置环境变量 `LLM_API_KEY`

### 2. 准备 MySQL（二选一）

**方式 A：使用本机 MySQL（当前已按此配置）**

本机 MySQL 8（root / 123456）运行在 3306，`application.yml` 已按此配置；启动时自动创建库表（`rag_kb`）。

**方式 B：Docker Compose 启动**

```bash
docker compose up -d        # 启动 MySQL 容器（库 rag_kb，root/123456）
```

### 3. 启动应用

```bash
mvn spring-boot:run
```

启动时自动建表：`document_meta`、`document_chunk`（向量 BLOB 字段）、`chat_session`、`chat_message`。

### 4. 验证

```bash
curl http://localhost:8080/api/document/list
# {"code":200,"message":"success","data":[]}
```

### 5. 前端

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173（/api 代理到 8080）
```

生产部署：`npm run deploy` 后将构建产物拷贝到后端 static，访问 `http://localhost:8080/` 同源运行。

## 接口文档

### 文档管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/document/upload` | 上传文档（multipart，字段 `file`，支持 pdf/txt，≤10MB） |
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

响应事件（`event: message`，JSON）：`delta`（增量）/ `done`（完整回答）/ `error`（友好错误信息）。

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
| `spring.ai.openai.api-key` | `${LLM_API_KEY}` | 智谱 Key（从环境变量 `LLM_API_KEY` 读取，启动前必须设置） |
| `spring.ai.openai.base-url` | `https://open.bigmodel.cn/api/paas/v4` | 对话服务地址（OpenAI 兼容） |
| `spring.ai.openai.chat.completions-path` | `/chat/completions` | 对话端点路径 |
| `spring.ai.openai.chat.options.model` | `glm-4.7-flash` | 对话模型名 |
| `spring.ai.openai.embedding.base-url` | `https://open.bigmodel.cn/api/paas/v4` | Embedding 服务地址 |
| `spring.ai.openai.embedding.embeddings-path` | `/embeddings` | Embedding 端点路径 |
| `spring.ai.openai.embedding.options.model` | `embedding-3` | 向量模型名 |
| `spring.ai.openai.embedding.options.dimensions` | `1024` | 向量维度（必须与 `rag.embedding-dimensions` 一致） |
| `rag.embedding-dimensions` | `1024` | 向量存储维度 |
| `rag.chunk-size` / `rag.overlap-size` | `500` / `50` | 分片大小 / 重叠长度（字符） |
| `rag.top-k` | `10` | 向量检索返回候选片段数 |
| `rag.similarity-threshold` | `0.3` | 余弦相似度阈值（低于过滤） |
| `rag.max-file-size` | `10485760` | 上传文件大小上限（10MB） |
| `rag.session.max-messages` / `summary-threshold` | `20` / `16` | 消息上限 / 压缩时保留的最近条数 |

### 切换其他模型服务商（如通义千问 DashScope）

简历技术栈为通义千问 API，如需切换到通义，仅需修改 `application.yml` 中 4 个配置项：

```yaml
spring:
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY:sk-你的DashScopeKey}      # 通义 Key（sk- 开头）
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      chat:
        options:
          model: qwen-plus                  # 通义对话模型
      embedding:
        options:
          model: text-embedding-v3          # 通义向量模型（1024 维）
```

同时把前端 `frontend/src/App.vue` 中的模型名文案改为对应模型即可。

## 业务容错设计

1. **文件校验**：上传时校验扩展名（仅 pdf/txt）与大小（≤10MB），`spring.servlet.multipart` 与业务层双重兜底
2. **API 异常分类**（`GlobalExceptionHandler`）：
   - 连接超时 / 网络不可达（`ResourceAccessException`）→ `503 模型服务连接超时或不可达，请稍后再试`
   - 限流（`429 Too Many Requests`）→ `429 模型服务繁忙（触发限流），请稍后再试`
   - Key 无效 / 模型不存在等客户端错误（`HttpClientErrorException`）→ `502 模型服务调用失败，请检查 API Key 与模型配置后重试`
3. **链路降级**：Query Rewrite 失败回退原问题；摘要生成失败跳过压缩；单分片向量化失败跳过该片；SSE 全程异常均推送友好 `error` 事件
4. **兜底**：未分类异常统一返回 `服务暂时不可用，请稍后再试`，不向客户端泄露内部堆栈

## 核心实现要点

### 重叠分片（ChunkingService）

1. 按空行切段落，保留段落语义；
2. 段落合并为接近 chunkSize 的块；
3. 超长块滑动窗口切分，窗口 = chunkSize，步长 = chunkSize − overlapSize；
4. 窗口未到文末时在句子边界（。！？.；）处断开，避免从句子中间截断。

### MySQL 向量存储（MysqlVectorService）

- 向量与业务数据**同库存储**：`document_chunk.embedding`（BLOB）保存归一化后的 float32 数组（1024 维 × 4 字节）
- 归一化后余弦相似度 = 点积，检索时应用层线性扫描取 TopK（文档量级下性能足够；海量向量可升级 MySQL 9.0 VECTOR 类型或专用向量库）

### Query Rewrite（QueryRewriteService）

首轮直接使用原问题；多轮时把历史对话 + 当前问题交给大模型，改写为不依赖上下文的独立问题，解决“它 / 这个 / 上面提到”等指代歧义；失败自动回退原问题。

### 长对话摘要压缩（SessionService）

单会话消息超过 `max-messages`（20）时，保留最近 16 条完整消息，更早消息交给大模型生成 ≤300 字摘要，以 system 角色消息写入 `chat_message` 表，防止上下文超限。

### SSE 流式（ChatService）

`SseEmitter(0L)` 不超时；`chatModel.stream()` 的 `Flux<ChatResponse>` 订阅回调中逐段发送 `delta` 事件，结束后持久化本轮问答并发送 `done`。

## 常见问题

**Q：启动报错无法连接数据库？**
确认 MySQL 已启动（本机服务或 `docker compose up -d`），且 `application.yml` 账号密码正确（默认 root / 123456）；库 `rag_kb` 与表由 `spring.sql.init` 自动创建，无需手工执行。

**Q：调用模型报 401 / 认证失败？**
需通过环境变量 `LLM_API_KEY` 提供智谱 Key；如认证失败，检查 `spring.ai.openai.api-key` 是否已注入（智谱开放平台 https://open.bigmodel.cn/ 获取）。

**Q：回答质量差 / 幻觉？**
1) 检查文档是否 `SUCCESS`（`GET /api/document/list`）；2) 调小 `rag.similarity-threshold` 召回更多候选；3) 调大 `rag.top-k` 增加上下文；4) 调小 `rag.chunk-size` 减少噪音。

**Q：向量维度相关？**
更换 Embedding 模型时同步修改 `spring.ai.openai.embedding.options.dimensions` 与 `rag.embedding-dimensions`，并删除旧文档重新上传（`document_chunk` 中的旧向量维度不匹配会被跳过）。
