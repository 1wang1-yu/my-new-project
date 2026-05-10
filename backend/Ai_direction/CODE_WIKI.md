# AI数字人导游项目 - Code Wiki

## 1. 项目概述

### 1.1 项目简介
这是一个基于AI大模型的智能景区导览系统，为游客提供7x24小时在线的智能导游服务。项目采用前后端分离架构，集成了智能问答、语音识别、语音合成、知识库检索、路线推荐等核心功能。

### 1.2 技术特点
- **核心技术栈**：Spring Boot 3.4.2 + Spring AI + Vue 3 + SQL Server + ChromaDB
- **核心能力**：RAG检索增强生成、语音交互、智能路线推荐
- **外部集成**：阿里通义大模型、Whisper语音识别、TTS语音合成

---

## 2. 项目架构

### 2.1 整体架构图
```
┌─────────────────────────────────────────────────────────────────┐
│                        游客端（小程序/App）                        │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Spring Boot 后端 (8081)                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │ChatController│  │AsrController│  │TtsController│  │AdminController│ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬───┘ │
│         │                │                │                │       │
│         ▼                ▼                ▼                ▼       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │ ChatService │  │ AsrService  │  │ TtsService  │  │ Analytics │ │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬───┘ │
│         │                │                │                │       │
│         ▼                ▼                ▼                ▼       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │ LlmClient   │  │WhisperClient│  │ VectorDb    │  │  Mapper  │ │
│  │ (大模型)    │  │ (语音识别)   │  │ (ChromaDB)  │  │ (SQL Server) │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬───┘ │
└─────────┼────────────────┼────────────────┼────────────────┼───────┘
          │                │                │                │
          ▼                ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      外部API服务                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │ 阿里通义    │  │ 阿里 Whisper │  │ 阿里 TTS    │  │ SQL Server│ │
│  │ qwen3.5     │  │ 语音识别     │  │ 语音合成    │  │ 1433端口 │ │
│  └─────────────┘  └─────────────┘  └─────────────┘  └──────────┘ │
│                                                                     │
│  ┌─────────────┐                                                   │
│  │ ChromaDB    │                                                   │
│  │ 8000端口    │                                                   │
│  │ (知识库)    │                                                   │
│  └─────────────┘                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

| 模块 | 目录 | 职责 |
|------|------|------|
| guide-common | `guide-common/` | 公共配置、工具类、统一响应封装、异常定义 |
| guide-pojo | `guide-pojo/` | 实体类、DTO数据传输对象 |
| guide-server | `guide-server/` | Web服务层、Controller、Service、Mapper、外部客户端 |
| guide-frontend | `guide-frontend/` | Vue前端管理后台 |

---

## 3. 主要模块职责

### 3.1 guide-common（公共模块）
提供跨模块共享的基础设施：
- **常量定义**：[HttpStatusConstant.java](file:///d:/Ai_direction/guide-common/src/main/java/com/guide/common/constant/HttpStatusConstant.java)、[MessageConstant.java](file:///d:/Ai_direction/guide-common/src/main/java/com/guide/common/constant/MessageConstant.java)
- **统一响应**：[Result.java](file:///d:/Ai_direction/guide-common/src/main/java/com/guide/common/result/Result.java)
- **异常处理**：[BaseException.java](file:///d:/Ai_direction/guide-common/src/main/java/com/guide/common/exception/BaseException.java)
- **工具类**：[StringUtil.java](file:///d:/Ai_direction/guide-common/src/main/java/com/guide/common/utils/StringUtil.java)
- **上下文**：[BaseContext.java](file:///d:/Ai_direction/guide-common/src/main/java/com/guide/common/context/BaseContext.java)

### 3.2 guide-pojo（数据模型模块）
定义系统数据结构：
- **实体类**：[Admin.java](file:///d:/Ai_direction/guide-pojo/src/main/java/com/guide/entity/Admin.java)、[User.java](file:///d:/Ai_direction/guide-pojo/src/main/java/com/guide/entity/User.java)、[ChatMessage.java](file:///d:/Ai_direction/guide-pojo/src/main/java/com/guide/entity/ChatMessage.java)、[Session.java](file:///d:/Ai_direction/guide-pojo/src/main/java/com/guide/entity/Session.java)、[KnowledgeDoc.java](file:///d:/Ai_direction/guide-pojo/src/main/java/com/guide/entity/KnowledgeDoc.java)、[DigitalAvatar.java](file:///d:/Ai_direction/guide-pojo/src/main/java/com/guide/entity/DigitalAvatar.java)、[ScenicSpot.java](file:///d:/Ai_direction/guide-pojo/src/main/java/com/guide/entity/ScenicSpot.java)、[Route.java](file:///d:/Ai_direction/guide-pojo/src/main/java/com/guide/entity/Route.java)
- **DTO类**：请求和响应数据传输对象

### 3.3 guide-server（后端服务模块）

#### 3.3.1 Controller层（控制器）
处理HTTP请求：

| 控制器 | 路径 | 功能 |
|--------|------|------|
| [ChatController](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/controller/ChatController.java) | `/api/v1/chat` | 智能问答核心接口 |
| [AsrController](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/controller/AsrController.java) | `/api/v1/asr` | 语音识别接口 |
| [TtsController](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/controller/TtsController.java) | `/api/v1/tts` | 语音合成接口 |
| [RouteController](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/controller/RouteController.java) | `/api/v1/route` | 路线推荐接口 |
| [AdminController](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/controller/AdminController.java) | `/api/admin` | 管理后台接口 |

#### 3.3.2 Service层（业务逻辑）
核心业务处理：

| 服务类 | 主要职责 |
|--------|----------|
| [ChatService](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/service/ChatService.java) | 协调整个RAG流程、调用大模型、处理聊天会话 |
| [KnowledgeService](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/service/KnowledgeService.java) | 知识库检索、文档管理 |
| [TtsService](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/service/TtsService.java) | 语音合成服务 |
| [AsrService](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/service/AsrService.java) | 语音识别服务 |
| [AnalyticsService](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/service/AnalyticsService.java) | 数据分析服务 |

#### 3.3.3 Client层（外部API客户端）
封装第三方服务调用：

| 客户端 | 功能 |
|--------|------|
| [LlmClient](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/client/LlmClient.java) | 调用阿里通义大模型API |
| [VectorDbClient](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/client/VectorDbClient.java) | 操作ChromaDB向量数据库 |
| [WhisperClient](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/client/WhisperClient.java) | 调用Whisper语音识别API |

#### 3.3.4 Mapper层（数据访问）
使用Spring Data JPA进行数据库操作：

| Mapper | 对应表 | 功能 |
|--------|--------|------|
| ChatMessageMapper | t_chat_message | 聊天记录管理 |
| GuideSessionMapper | t_session | 会话管理 |
| GuideUserMapper | t_user | 用户管理 |
| AdminMapper | t_admin | 管理员管理 |
| KnowledgeDocMapper | t_knowledge_doc | 知识库文档管理 |
| DigitalAvatarMapper | t_digital_avatar | 数字人配置 |
| ScenicSpotMapper | t_scenic_spot | 景点信息 |
| TravelRouteMapper | t_route | 游览路线 |

#### 3.3.5 Config层（配置）
系统配置类：

| 配置类 | 功能 |
|--------|------|
| [GuideProperties](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/config/GuideProperties.java) | 读取application.yml中的guide配置项 |
| [RestClientConfig](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/config/RestClientConfig.java) | RestClient配置 |
| [WebMvcConfig](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/config/WebMvcConfig.java) | Web MVC配置 |

#### 3.3.6 AOP与拦截器
- [LoggingAspect](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/aspect/LoggingAspect.java)：日志切面
- [LoginInterceptor](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/interceptor/LoginInterceptor.java)：登录拦截器
- [GlobalExceptionHandler](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/handler/GlobalExceptionHandler.java)：全局异常处理器

### 3.4 guide-frontend（前端管理后台）

Vue 3 + Ant Design Vue构建的管理后台，包含以下页面：

| 页面 | 路径 | 功能 |
|------|------|------|
| Dashboard | `/` | 数据概览 |
| Knowledge | `/knowledge` | 知识库管理 |
| Avatar | `/avatar` | 数字人配置 |
| Data | `/data` | 数据分析 |
| Settings | `/settings` | 系统设置 |
| Tourist | `/tourist` | 游客端模拟 |

---

## 4. 核心技术栈

### 4.1 后端技术
| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.2 | 核心框架 |
| Spring Data JPA | 3.x | ORM框架 |
| Spring AI | 1.0.0-M6 | AI集成框架 |
| SQL Server | - | 关系型数据库 |
| Lombok | - | 简化代码 |
| Swagger/SpringDoc | 2.8.5 | API文档 |

### 4.2 前端技术
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.13 | 前端框架 |
| Vue Router | 4.4.5 | 路由管理 |
| Ant Design Vue | 4.2.5 | UI组件库 |
| Axios | 1.7.9 | HTTP客户端 |
| ECharts | 5.5.1 | 数据可视化 |
| Vite | 6.0.5 | 构建工具 |

### 4.3 外部服务
| 服务 | 用途 |
|------|------|
| 阿里通义（qwen3.5-flash） | 大语言模型 |
| ChromaDB | 向量数据库（知识库存储） |
| Whisper API | 语音识别 |
| TTS API | 语音合成 |

---

## 5. 核心业务流程

### 5.1 智能问答流程（RAG核心）
```
用户提问："断桥有什么历史故事？"
          │
          ▼
┌─────────────────────────────────┐
│  ChatController.chat()         │
│  接收用户问题                   │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  KnowledgeService               │
│  retrieveContext()              │
│  从ChromaDB检索相关知识         │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  构建提示词：                   │
│  "用户问题：断桥有什么历史故事？│
│   知识库：[相关文档1,文档2...]" │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  LlmClient.chat()               │
│  调用阿里通义大模型             │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  提取回答和建议追问             │
│  返回AI回答给用户               │
└─────────────────────────────────┘
```

### 5.2 语音交互流程
```
用户：点击语音按钮，说"断桥怎么走"
      │
      ▼
┌─────────────────────────────────┐
│  AsrController.transcribe()    │
│  接收音频base64                 │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  WhisperClient.transcribe()     │
│  调用阿里Whisper API            │
│  转换为文字："断桥怎么走"       │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  ChatService.chat()             │
│  走RAG流程获取AI回答            │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  TtsService.synthesize()        │
│  调用阿里TTS API                │
│  将文字转换为语音               │
└────────────┬────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│  返回 { answer, tts_url, emotion }│
│  给游客端播放语音               │
└─────────────────────────────────┘
```

---

## 6. 核心API接口

### 6.1 智能问答
- **接口**：`POST /api/v1/chat`
- **功能**：接收游客问题，返回AI回答
- **请求体**：
```json
{
  "user_id": 1,
  "session_id": "uuid",
  "message": "断桥有什么历史？",
  "input_type": "text"
}
```
- **响应体**：
```json
{
  "code": 200,
  "data": {
    "answer": "断桥位于西湖...",
    "tts_url": "https://...",
    "emotion": "positive",
    "suggested_questions": ["问题1", "问题2", "问题3"],
    "session_id": "uuid"
  }
}
```

### 6.2 语音识别
- **接口**：`POST /api/v1/asr`
- **功能**：将语音转换为文字
- **请求体**：
```json
{
  "audio_data": "base64编码的音频"
}
```

### 6.3 语音合成
- **接口**：`POST /api/v1/tts`
- **功能**：将文字转换为语音
- **请求体**：
```json
{
  "text": "要合成的文字",
  "voice_id": "guide-default",
  "speed": 1.0,
  "emotion": "positive"
}
```

### 6.4 路线推荐
- **接口**：`POST /api/v1/route/recommend`
- **功能**：根据用户兴趣推荐游览路线

---

## 7. 数据库设计

### 7.1 主要数据表

| 表名 | 用途 |
|------|------|
| t_chat_message | 聊天记录 |
| t_session | 会话管理 |
| t_user | 用户信息 |
| t_admin | 管理员信息 |
| t_knowledge_doc | 知识库文档 |
| t_digital_avatar | 数字人配置 |
| t_scenic_spot | 景点信息 |
| t_route | 游览路线 |
| t_route_spot | 路线-景点关联 |

---

## 8. 项目启动与配置

### 8.1 环境要求
- JDK 17+
- Node.js 18+
- SQL Server
- Docker (运行ChromaDB)

### 8.2 启动顺序

#### 8.2.1 启动基础服务
```bash
# 启动ChromaDB向量数据库
docker run -d -p 8000:8000 chromadb/chroma
```

#### 8.2.2 配置后端
编辑 `guide-server/src/main/resources/application-dev.yml`，配置：
- 数据库连接信息
- 阿里通义API密钥
- ChromaDB地址
- Whisper和TTS API配置

#### 8.2.3 启动后端
```bash
cd guide-server
mvn spring-boot:run
```

#### 8.2.4 启动前端
```bash
cd guide-frontend
npm install
npm run dev
```

### 8.3 访问地址
- 后端服务：http://localhost:8081
- API文档：http://localhost:8081/swagger-ui.html
- 前端管理后台：http://localhost:5173

---

## 9. 关键类与函数说明

### 9.1 LlmClient（大模型客户端）
**文件**：[LlmClient.java](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/client/LlmClient.java)

**核心方法**：
```java
public String chat(String systemPrompt, String userMessage)
```
- **功能**：调用阿里通义大模型API
- **参数**：
  - `systemPrompt`：系统提示词
  - `userMessage`：用户消息
- **返回**：AI生成的回答
- **实现方式**：直接HTTP调用，避免Spring AI版本兼容性问题

### 9.2 ChatService（聊天服务）
**文件**：[ChatService.java](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/service/ChatService.java)

**核心方法**：
```java
public ChatReply chat(Long userId, String sessionId, String userMessage, String inputType)
```
- **功能**：协调整个智能问答流程
- **流程**：
  1. 创建或获取会话
  2. 检索知识库
  3. 构建提示词
  4. 调用大模型
  5. 合成语音
  6. 保存聊天记录
  7. 返回回答

**内部辅助方法**：
- `extractAnswer()`：从AI响应中提取纯回答
- `extractSuggestedQuestions()`：提取建议追问问题
- `inferEmotion()`：根据回答内容推断情绪

### 9.3 KnowledgeService（知识服务）
**文件**：[KnowledgeService.java](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/service/KnowledgeService.java)

**核心方法**：
```java
public List<String> retrieveContext(String userQuery, List<List<Float>> queryEmbedding, int topK)
```
- **功能**：从知识库检索相关上下文
- **策略**：优先向量检索，降级为关键词检索

### 9.4 VectorDbClient（向量数据库客户端）
**文件**：[VectorDbClient.java](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/client/VectorDbClient.java)

**核心方法**：
```java
public List<String> query(String collectionName, List<List<Float>> queryEmbeddings, int nResults)
```
- **功能**：调用ChromaDB API进行语义检索

---

## 10. 依赖关系

### 10.1 Maven模块依赖
```
guide-parent (父工程)
├── guide-common (公共模块，被其他模块依赖)
├── guide-pojo (数据模型，被guide-server依赖)
└── guide-server (主服务，依赖common和pojo)
```

### 10.2 guide-server关键依赖
- `spring-boot-starter-web`：Web服务
- `spring-boot-starter-data-jpa`：JPA数据访问
- `spring-ai-openai-spring-boot-starter`：OpenAI/Spring AI集成
- `springdoc-openapi-starter-webmvc-ui`：Swagger文档
- `mssql-jdbc`：SQL Server驱动
- `lombok`：代码简化

---

## 11. 项目规范与约定

### 11.1 代码规范
- 使用Lombok简化代码
- 统一使用Result封装响应
- 异常通过GlobalExceptionHandler统一处理
- 使用@RequiredArgsConstructor进行依赖注入

### 11.2 配置管理
- 使用Spring Profile区分环境（dev/prod）
- 敏感配置通过环境变量注入
- 自定义配置通过@ConfigurationProperties读取

---

## 12. 与同类项目对比

### 12.1 vs 苍穹外卖（传统CRUD项目）

| 对比项 | 苍穹外卖 | AI数字人导游 |
|--------|----------|--------------|
| 核心框架 | Spring Boot + MyBatis Plus | Spring Boot + Spring AI |
| 数据库 | MySQL | SQL Server |
| 向量数据库 | 无 | ChromaDB |
| 核心功能 | CRUD + 订单管理 | AI问答 + 知识库检索 |
| 核心逻辑 | 查数据库 → 返回数据 | 检索知识库 → 调用AI → 返回回答 |
| AI能力 | 无 | 大模型API调用 |

---

## 13. 扩展建议

### 13.1 功能扩展
- 接入更多大模型（GPT、Claude、文心一言等）
- 增加多轮对话上下文管理
- 实现知识库文档自动分块与向量化
- 增加用户画像与个性化推荐

### 13.2 性能优化
- 添加Redis缓存热点数据
- 实现知识库向量检索的批量查询
- 添加异步调用优化响应速度

---

## 14. 常见问题排查

### 14.1 AI调用返回固定文案
- **原因**：大模型API调用失败，走到异常分支
- **排查**：
  - 检查API密钥是否正确
  - 检查base-url是否正确
  - 用Postman测试API是否可用

### 14.2 知识库检索返回空
- **原因**：ChromaDB没有启动或没有数据
- **排查**：
  - 检查ChromaDB是否在8000端口运行
  - 检查知识库是否已上传

### 14.3 语音识别失败
- **原因**：音频格式不对或API配置错误
- **排查**：
  - 检查音频是否为base64编码
  - 检查API密钥是否正确

---

## 15. 项目文件速查表

| 路径 | 说明 |
|------|------|
| [pom.xml](file:///d:/Ai_direction/pom.xml) | 父工程POM |
| [PROJECT_GUIDE.md](file:///d:/Ai_direction/PROJECT_GUIDE.md) | 项目快速上手指南 |
| [PROJECT_FLOW.md](file:///d:/Ai_direction/PROJECT_FLOW.md) | 核心流程图 |
| [GuideApplication.java](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/GuideApplication.java) | 启动类 |
| [application.yml](file:///d:/Ai_direction/guide-server/src/main/resources/application.yml) | 主配置文件 |
| [ChatController.java](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/controller/ChatController.java) | 聊天接口 |
| [ChatService.java](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/service/ChatService.java) | 聊天服务 |
| [LlmClient.java](file:///d:/Ai_direction/guide-server/src/main/java/com/guide/client/LlmClient.java) | 大模型客户端 |

---

## 总结

**一句话理解项目**：
> "这是一个用AI大模型回答景区问题的系统，知识库检索让AI知道景区的专业知识，ChromaDB存储这些知识，大模型负责理解和回答。"

**最核心的三个类**：
1. `LlmClient` - 调用大模型
2. `KnowledgeService` - 检索知识库
3. `ChatService` - 整合两者，协调整个流程

**项目成功的关键**：
- 正确配置API密钥
- 确保ChromaDB正常运行
- 知识库文档质量决定AI回答质量
