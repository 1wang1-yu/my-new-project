# AI数字人导游项目 - 快速上手指南

## 项目概述

这是一个基于AI大模型的智能景区导览系统，为游客提供7x24小时在线的智能导游服务。

## 与苍穹外卖对比

| 对比项 | 苍穹外卖 | AI数字人导游 |
|--------|----------|--------------|
| 核心框架 | Spring Boot + MyBatis Plus | Spring Boot + Spring AI |
| 数据库 | MySQL | SQL Server |
| 缓存 | Redis | ChromaDB（向量数据库） |
| 用户认证 | JWT | JWT |
| 核心功能 | CRUD + 订单管理 | AI问答 + 知识库检索 |
| AI能力 | 无 | 大模型API调用 |

## 项目架构

```
guide-server/
├── controller/          # 控制器层（与苍穹外卖一样）
│   ├── ChatController   # AI聊天接口
│   ├── AsrController   # 语音识别接口
│   ├── TtsController   # 语音合成接口
│   ├── RouteController # 路线推荐接口
│   └── AdminController # 管理后台接口
│
├── service/             # 服务层（与苍穹外卖一样）
│   ├── ChatService     # 处理聊天逻辑
│   ├── KnowledgeService # 知识库检索
│   ├── TtsService      # 语音合成
│   └── AnalyticsService # 数据分析
│
├── client/              # 外部API客户端（新增）
│   ├── LlmClient       # 调用大模型API
│   ├── WhisperClient   # 调用语音识别API
│   └── VectorDbClient  # 调用向量数据库
│
├── mapper/              # 数据访问层（JPA，与MyBatis不同）
│   └── ChatMessageMapper, SessionMapper...
│
└── config/              # 配置类
    └── GuideProperties  # 读取配置文件
```

## 核心技术对比学习

### 1. Spring AI（新增）

**类比理解**：Spring AI就像是"Spring Data JPA for AI"，它提供了统一的方式来调用各种AI模型（OpenAI、阿里通义、Claude等）。

**苍穹外卖中**：你没有AI调用，都是查数据库。

**这个项目中**：
```java
// LlmClient.java - 调用大模型
String response = llmClient.chat(systemPrompt, userMessage);
```

**快速理解**：
- `ChatClient.builder(chatModel)` - 构建AI客户端
- `.prompt().system().user()` - 构建提示词
- `.call().content()` - 执行调用获取结果

### 2. 向量数据库ChromaDB（新增）

**类比理解**：ChromaDB就像是一个"语义搜索引擎"，而MySQL是"精确匹配引擎"。

**苍穹外卖中**：
```sql
SELECT * FROM food WHERE name = '红烧肉'  -- 精确匹配
```

**这个项目中**：
```java
// KnowledgeService.java - 语义检索
List<String> context = knowledgeService.retrieveContext("附近有什么好吃的？", null, 4);
```

**快速理解**：
- 用户问"附近有什么好吃的？"会返回关于美食的文档
- 而不是必须精确匹配"好吃"这个关键词
- 这是AI回答的基础，让大模型知道景区的相关知识

### 3. RAG检索增强生成（核心流程）

**工作流程**：
```
用户问题 → 检索知识库 → 构建提示词 → 调用大模型 → 返回答案
    ↓
1. KnowledgeService.retrieveContext() 从ChromaDB检索相关文档
2. 将检索到的文档作为上下文
3. 构建完整的提示词（包含用户问题+知识库内容）
4. 调用大模型API获取回答
```

**苍穹外卖中没有的概念**：这是一个"让AI知道景区专业知识"的技术。

### 4. Whisper语音识别（新增）

**类比理解**：Whisper就像是一个"语音转文字"的工具。

**苍穹外卖中没有语音功能**。

**这个项目中**：
```java
// WhisperClient.java - 语音识别
String text = whisperClient.transcribe(audioBase64);
```

### 5. TTS语音合成（新增）

**类比理解**：TTS就像是一个"文字转语音"的工具。

**这个项目中**：
```java
// TtsService.java - 语音合成
Map<String, Object> result = ttsService.synthesize(text, voiceId, speed, emotion);
```

## 项目启动顺序

### 1. 启动基础服务
```bash
# 1. 启动SQL Server（数据库）
# 2. 启动ChromaDB（向量数据库）
docker run -d -p 8000:8000 chromadb/chroma
```

### 2. 启动后端
```bash
cd guide-server
mvn spring-boot:run
```

### 3. 启动前端
```bash
cd guide-frontend
npm run dev
```

## 核心接口说明

### 1. 智能问答（最核心）
```
POST /api/v1/chat
Body: { session_id, message, user_id }

工作流程：
1. 检索知识库获取相关文档
2. 构建提示词（用户问题+知识库）
3. 调用大模型API
4. 返回AI回答
```

### 2. 语音识别
```
POST /api/v1/asr
Body: { audio_base64 }

工作流程：
1. 接收语音数据
2. 调用Whisper API转换为文字
3. 返回文字结果
```

### 3. 语音合成
```
POST /api/v1/tts
Body: { text, voice_id, speed, emotion }

工作流程：
1. 接收文本
2. 调用TTS API转换为语音
3. 返回语音URL
```

### 4. 路线推荐
```
POST /api/v1/route/recommend
Body: { user_id, interests[], duration_minutes }

工作流程：
1. 根据用户兴趣筛选景点
2. 生成推荐路线
3. 返回路线详情
```

## 配置文件说明

### application-dev.yml
```yaml
spring:
  ai:
    openai:
      api-key: sk-xxx          # 阿里云API密钥
      base-url: https://xxx    # API地址
      chat:
        options:
          model: qwen3.5-flash # 使用的模型

guide:
  chroma:
    base-url: http://localhost:8000  # 向量数据库地址
  whisper:
    base-url: https://xxx       # 语音识别API地址
  tts:
    base-url: https://xxx       # 语音合成API地址
```

## 快速开发建议

### 1. 先跑通智能问答
- 启动ChromaDB
- 检查API密钥配置
- 调用 `/api/v1/chat` 接口
- 看是否能返回AI回答

### 2. 调试知识库检索
- 调用 `/api/admin/knowledge/upload` 上传文档
- 调用 `/api/v1/chat` 问相关问题
- 看是否基于上传的文档回答

### 3. 对比苍穹外卖的开发模式
- **苍穹外卖**：写SQL → Mapper → Service → Controller
- **这个项目**：调用API → Service处理 → Controller返回

## 常见问题排查

### 1. AI调用返回固定文案
**原因**：大模型API调用失败，走到了兜底逻辑
**排查**：
- 检查API密钥是否正确
- 检查base-url是否正确
- 用Postman测试API是否可用

### 2. 知识库检索返回空
**原因**：ChromaDB没有启动或没有数据
**排查**：
- 检查ChromaDB是否在8000端口运行
- 检查知识库是否已上传

### 3. 语音识别失败
**原因**：音频格式不对或API配置错误
**排查**：
- 检查音频是否为base64编码
- 检查API密钥是否正确

## 学习路径建议

1. **第一阶段**：理解项目结构，对比苍穹外卖
2. **第二阶段**：跑通智能问答接口（最核心）
3. **第三阶段**：理解知识库检索原理
4. **第四阶段**：掌握RAG流程（检索+生成）
5. **第五阶段**：扩展语音识别和合成功能

## 核心代码文件

| 文件 | 作用 | 重要性 |
|------|------|--------|
| `LlmClient.java` | 调用大模型API | ⭐⭐⭐⭐⭐ |
| `KnowledgeService.java` | 知识库检索 | ⭐⭐⭐⭐⭐ |
| `ChatService.java` | 处理聊天逻辑 | ⭐⭐⭐⭐⭐ |
| `VectorDbClient.java` | 操作向量数据库 | ⭐⭐⭐⭐ |
| `WhisperClient.java` | 语音识别 | ⭐⭐⭐ |
| `TtsService.java` | 语音合成 | ⭐⭐⭐ |

## 技术栈总结

| 技术 | 作用 | 类比 |
|------|------|------|
| Spring AI | 调用AI模型 | 类似Spring Data |
| ChromaDB | 存储景区知识 | 类似MySQL |
| RAG | 检索+生成回答 | 新概念 |
| Whisper | 语音→文字 | 新技术 |
| TTS | 文字→语音 | 新技术 |
| JWT | 用户认证 | 与苍穹外卖一样 |

---

**关键理解**：这个项目的核心不是CRUD，而是"让AI能够回答景区专业问题"。所有技术都是围绕这个目标展开的。