# FileAgent - AI文件助手

基于Spring Alibaba AI的智能文件管理助手，支持流式对话和本地文件操作。

## 功能特性

- 智能对话：集成通义千问大模型
- 流式响应：实时显示AI回复
- 文件操作Skills：
  - `@list_disk` - 查看系统磁盘信息
  - `@list_files <路径>` - 列出目录内容
  - `@get_file_size <路径>` - 获取文件大小
  - `@read_file <路径> [行数]` - 读取文件内容
  - `@create_file <路径> [内容]` - 创建新文件
  - `@edit_file <路径> <新内容>` - 编辑文件
  - `@delete_file <路径>` - 删除文件或目录

## 环境要求

- JDK 17+
- Maven 3.6+

## 配置步骤

### 1. 配置通义千问API Key

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

### 2. 配置允许操作的路径（可选）

在 `application.yml` 中配置允许操作的基础路径：

```yaml
file-agent:
  allowed-base-paths:
    - D:/workspace
    - C:/temp
    - ./data
```

## 运行项目

```bash
cd fileAgent
mvn spring-boot:run
```

## 访问应用

打开浏览器访问：http://localhost:8080

## 使用示例

### 普通对话
直接输入问题即可与AI对话。

### 文件操作

在聊天窗口中使用以下命令：

```
@list_disk
```
查看所有磁盘信息

```
@list_files D:/workspace
```
列出指定目录的文件和子目录

```
@get_file_size D:/workspace/test.txt
```
获取文件大小

```
@read_file D:/workspace/test.txt
```
读取文件内容（默认前100行）

```
@create_file D:/workspace/new.txt 你好世界
```
创建新文件

```
@edit_file D:/workspace/test.txt 这是新的内容
```
编辑文件内容

```
@delete_file D:/workspace/old.txt
```
删除文件

## 技术栈

- Spring Boot 3.2.5
- Spring Alibaba AI 1.0.0-M6.1
- DashScope (通义千问)
- Spring WebFlux (流式响应)
- Lombok

## 项目结构

```
fileAgent/
├── src/main/java/com/example/fileagent/
│   ├── controller/          # Controller层 - REST API
│   ├── service/             # Service层 - 业务逻辑
│   ├── skill/               # Skills - 文件操作实现
│   ├── model/               # 数据模型
│   └── FileAgentApplication.java
├── src/main/resources/
│   ├── static/
│   │   └── index.html       # 聊天界面
│   └── application.yml      # 配置文件
└── pom.xml
```

## 注意事项

1. 文件操作请在安全的路径下进行
2. 建议先在测试环境中验证功能
3. 删除操作不可恢复，请谨慎使用
