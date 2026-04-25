# FileAgent：基于Spring Alibaba AI的智能文件管理助手

## 前言

在人工智能快速发展的今天，如何将大语言模型与本地文件系统相结合，创造出真正实用的智能助手，成为了一个重要的研究方向。本文将介绍一个名为FileAgent的创新项目，它基于Spring Alibaba AI框架，实现了与通义千问大模型的深度集成，为用户提供了一个功能强大的智能文件管理助手。

**🎉 项目已开源！**  
GitHub/Gitee 仓库地址：**https://gitee.com/mobuhan/file-management-knowledge-base.git**

欢迎 Star ⭐、Fork 和贡献代码！

## 项目概述

FileAgent是一个基于Spring Boot和Spring Alibaba AI构建的智能文件管理助手，它具备以下核心特性：

- **智能对话**：集成通义千问大模型，支持自然语言交互
- **流式响应**：实时显示AI回复，提升用户体验
- **文件操作Skills**：提供7种文件操作技能，包括磁盘查看、文件列表、大小获取、读写编辑等
- **知识库功能**：支持多种文档格式的上传和检索，实现RAG（检索增强生成）
- **多模态支持**：支持图片、PDF、Word等文件的直接上传和分析
- **会话管理**：完整的聊天历史记录和会话管理功能

## 技术架构

### 后端技术栈

- **Spring Boot 3.2.5**：作为基础框架
- **Spring Alibaba AI 1.0.0-M6.1**：集成阿里云通义千问大模型
- **Spring WebFlux**：实现流式响应
- **Apache POI & PDFBox**：处理Office文档和PDF文件
- **Lombok**：简化Java代码

### 前端技术

- **原生HTML/CSS/JavaScript**：轻量级前端界面
- **Server-Sent Events (SSE)**：实现流式数据传输

## 核心功能详解

### 1. 智能文件操作技能系统

FileAgent采用创新的Skills架构，将文件操作封装为独立的技能模块：

```java
// 文件操作技能接口
public interface FileSkill {
    String getName();
    String getDescription();
    String execute(String... args);
}
```

系统内置7种核心技能：
- `@list_disk` - 查看系统磁盘信息
- `@list_files <路径>` - 列出目录内容
- `@get_file_size <路径>` - 获取文件大小
- `@read_file <路径> [行数]` - 读取文件内容
- `@create_file <路径> [内容]` - 创建新文件
- `@edit_file <路径> <新内容>` - 编辑文件
- `@delete_file <路径>` - 删除文件或目录

### 2. RAG知识库系统

FileAgent实现了完整的RAG（检索增强生成）功能，支持多种文档格式：

- **支持的格式**：TXT、DOC、DOCX、PDF、PPT、PPTX、XLS、XLSX
- **文本分块**：自动将文档分割为500字符的片段，带100字符重叠
- **关键词搜索**：基于关键词匹配的相关性检索
- **持久化存储**：知识库数据持久化到本地文件系统

### 3. 多模态文件处理

系统支持直接上传图片和文档进行分析：

```java
private UserMessage buildMultimodalMessage(String text, List<String> filePaths, List<String> fileNames) {
    // 处理Data URL格式的文件
    // 图片作为Media对象发送
    // 文档提取文本后嵌入消息
}
```

### 4. 流式对话体验

通过Spring WebFlux实现真正的流式响应：

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestBody ChatRequest request) {
    // 返回Flux<String>实现流式传输
}
```

## 系统架构设计

### 分层架构

```
┌─────────────────┐
│   Controller层   │  ← REST API接口
├─────────────────┤
│   Service层     │  ← 业务逻辑处理
├─────────────────┤
│   Skill层       │  ← 文件操作技能
├─────────────────┤
│   Model层       │  ← 数据模型
└─────────────────┘
```

### 核心组件

1. **ChatController**：处理聊天请求，支持流式和非流式响应
2. **KnowledgeBaseController**：管理知识库上传和检索
3. **ChatService**：核心聊天逻辑，集成AI模型和工具调用
4. **KnowledgeBaseService**：文档解析、分块和检索
5. **SkillManager**：技能注册和执行管理

## 关键技术实现

### 1. 工具调用机制

FileAgent使用特殊的标记语法实现AI对文件操作的自主调用：

```java
private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("\\[TOOL_CALL:([^\\]]+)\\]");

// AI回复示例："让我帮你查看D盘的内容 [TOOL_CALL:listFiles|D:/]"
```

### 2. 会话管理

每个聊天会话都有独立的ID和历史记录：

```java
private final Map<String, List<Message>> sessionHistory = new ConcurrentHashMap<>();
```

### 3. 安全限制

通过配置允许的操作路径来保障系统安全：

```yaml
file-agent:
  allowed-base-paths:
    - D:/workspace
    - C:/temp
    - ./data
```

## 部署和使用

### 环境要求

- JDK 17+
- Maven 3.6+
- 通义千问API密钥

### 快速启动

1. **克隆项目**：
```bash
git clone https://gitee.com/mobuhan/file-management-knowledge-base.git
cd fileAgent
```

2. **配置API密钥**：

在 `src/main/resources/application.yml` 中修改：

```yaml
spring:
  ai:
    dashscope:
      api-key: your-dashscope-api-key-here
```

或者设置环境变量：
```bash
export DASHSCOPE_API_KEY=your-api-key-here
```

获取API Key：访问 https://dashscope.console.aliyun.com/

3. **运行项目**：
```bash
mvn spring-boot:run
```

4. **访问应用**：http://localhost:8080

### 使用示例

```
用户: 帮我看看有哪些磁盘
AI: [调用listDisk技能] 系统共有C盘(500GB)、D盘(1TB)两个磁盘

用户: 列出D:/workspace文件夹
AI: [调用listFiles技能] D:/workspace包含以下文件...

用户: 读取D:/test.txt的内容
AI: [调用readFile技能] 文件内容如下...
```

## 创新亮点

### 1. Skills架构设计

将文件操作抽象为可插拔的技能模块，便于扩展和维护。

### 2. 混合检索策略

结合关键词匹配和语义理解，提高检索准确性。

### 3. 多模态融合

无缝整合文本、图片、文档等多种媒体类型的处理能力。

### 4. 持久化存储

所有会话数据和知识库都持久化到本地，重启不丢失。

## 应用场景

1. **开发辅助**：快速浏览代码结构、查找文件内容
2. **文档管理**：智能检索和分析各类办公文档
3. **学习助手**：基于个人知识库的个性化问答
4. **运维支持**：监控系统文件和目录状态

## 项目结构

```
fileAgent/
├── src/main/java/com/example/fileagent/
│   ├── controller/          # Controller层 - REST API
│   │   ├── ChatController.java
│   │   └── KnowledgeBaseController.java
│   ├── service/             # Service层 - 业务逻辑
│   │   ├── ChatService.java
│   │   ├── KnowledgeBaseService.java
│   │   ├── ChatHistoryService.java
│   │   └── FileAttachmentService.java
│   ├── skill/               # Skills - 文件操作实现
│   │   ├── FileTools.java
│   │   ├── SkillManager.java
│   │   ├── ListDiskSkill.java
│   │   ├── ListFilesSkill.java
│   │   ├── GetFileSizeSkill.java
│   │   ├── ReadFileSkill.java
│   │   ├── CreateFileSkill.java
│   │   ├── EditFileSkill.java
│   │   └── DeleteFileSkill.java
│   ├── model/               # 数据模型
│   └── FileAgentApplication.java
├── src/main/resources/
│   ├── static/
│   │   └── index.html       # 聊天界面
│   └── application.yml      # 配置文件
└── pom.xml
```

## API接口说明

### 聊天接口

- **POST /api/chat** - 非流式聊天
- **POST /api/chat/stream** - 流式聊天（SSE）
- **GET /api/chat/sessions** - 获取会话列表
- **GET /api/chat/session/{sessionId}** - 获取会话历史
- **DELETE /api/chat/session/{sessionId}** - 删除会话

### 知识库接口

- **POST /api/kb/upload** - 上传文件到知识库
- **GET /api/kb/files?sessionId=xxx** - 获取已上传文件列表
- **GET /api/kb/search?sessionId=xxx&query=xxx** - 搜索知识库
- **DELETE /api/kb/session/{sessionId}** - 清空会话知识库
- **DELETE /api/kb/all** - 清空所有知识库

## 未来展望

1. **向量数据库集成**：引入专业的向量存储提升检索效果
2. **更多文件格式**：支持更多专业文档格式
3. **权限管理**：细粒度的文件访问控制
4. **插件系统**：开放第三方技能开发接口
5. **多语言支持**：国际化界面和文档

## 常见问题

### Q: 如何获取通义千问API密钥？

A: 访问阿里云DashScope控制台 https://dashscope.console.aliyun.com/ 注册并创建API密钥。

### Q: 支持哪些操作系统？

A: 理论上支持所有支持JDK 17+的操作系统，已在Windows上测试通过。

### Q: 文件操作是否安全？

A: 系统通过`allowed-base-paths`配置限制可操作的路径范围，建议在配置文件中明确指定允许的路径。

### Q: 知识库数据保存在哪里？

A: 默认保存在 `D:/chat/kb/` 目录下，包括原始文件、分块数据和元数据。

## 总结

FileAgent展示了如何将大语言模型与传统文件系统有机结合，创造出一个既智能又实用的文件管理助手。通过创新的Skills架构和完善的RAG系统，它不仅能够理解用户的自然语言指令，还能准确执行各种文件操作，为用户提供了全新的交互体验。

该项目的源代码完全开源，欢迎感兴趣的开发者参与贡献和改进。

---

## 相关链接

- **Gitee仓库**: https://gitee.com/mobuhan/file-management-knowledge-base.git
- **通义千问官网**: https://tongyi.aliyun.com/
- **Spring Alibaba AI**: https://github.com/alibaba/spring-ai-alibaba

## 作者简介

本文作者是一名专注于AI应用开发的软件工程师，致力于探索大语言模型在实际业务场景中的创新应用。

## 版权声明

本文为原创技术文章，转载请注明出处。项目代码遵循开源协议，欢迎学习和使用。

---

**如果觉得这个项目对你有帮助，欢迎给个 Star ⭐ 支持一下！**
