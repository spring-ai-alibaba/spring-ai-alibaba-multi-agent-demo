# spring-ai-alibaba-multi-agent-demo
Cloud-Side Beverage Shop Smart Order Assistant - A distributed multi-agent system built using Spring AI Alibaba Agentic API

## 环境要求

在开始之前，请确保您的系统已安装以下软件：

- **Docker**: 用于运行中间件服务（MySQL、Nacos、Redis）
- **Java 17+**: 用于运行Spring Boot应用
- **Node.js 20+**: 用于构建和运行前端应用
- **Maven**: 用于构建Java项目

## 启动服务

### 步骤 1: 上传知识库到百炼

在启动应用服务之前，需要将咨询子智能体的知识库文件上传到阿里云百炼知识库。

知识库文件位于 `consult-sub-agent/src/main/resources/kownledge/`, 包含两个文件：
- `brand-overview.md`: 品牌概览和理念
- `products.md`: 产品详细介绍

创建知识库并上传以上文件, 并获取知识库ID。

### 步骤 2: 环境变量配置

本项目使用环境变量进行配置管理。运行前需要将环境变量配置到.env文件中。

#### 必需配置
确保功能完整运行的主要配置包括:
- `DASHSCOPE_API_KEY`: DashScope API 密钥（阿里云通义千问API）
- `DASHSCOPE_INDEX_ID`: DashScope 知识库 ID（百炼知识库ID）
- `MEM0_API_KEY`: Mem0 API密钥（用于记忆管理）

#### 获取API密钥
- **DashScope API**: 访问 [阿里云DashScope控制台](https://dashscope.console.aliyun.com/) 获取API密钥
- **百炼知识库ID**: 访问 [阿里云百炼控制台](https://bailian.console.aliyun.com/) 创建知识库并获取ID
- **Mem0 API**: 访问 [Mem0官网](https://mem0.ai/) 注册并获取API密钥


### 步骤 3: 启动基础服务（Docker Compose）

启动MySQL、Nacos和Redis等基础服务：

```bash
# 进入中间件目录
cd docker/middleware

# 启动基础服务
docker-compose up -d mysql nacos redis

# 等待服务启动完成（约30-60秒）
# 可以通过以下命令查看服务状态
docker-compose ps
```

### 步骤 4: 初始化 Nacos 控制台

在启动应用服务之前，必须先初始化Nacos控制台账号密码。
- 打开浏览器访问: http://localhost:8848/nacos
- 初始化Nacos账号和密码均为:nacos

### 步骤 5: 启动应用服务

完成Nacos初始化后，返回项目根目录启动应用服务：

```bash
# 返回项目根目录
cd ../..

# 启动所有应用服务
./build.sh
```

## 停止服务

```bash
# 停止应用服务
./stop.sh

# 停止基础服务（可选）
cd docker/middleware
docker-compose down
```

## 服务访问地址

启动完成后，您可以通过以下地址访问各个服务：

### 用户界面
- **前端界面**: http://localhost:3000 - 智能客服聊天界面

### 管理控制台
- **Nacos控制台**: http://localhost:8848/nacos
  - 用户名: `nacos`
  - 密码: `nacos`
  - 用于服务注册发现和配置管理

### 后端服务API
- **监督者智能体**: http://localhost:10008 - 主控制器
- **咨询子智能体**: http://localhost:10005 - 处理咨询请求
- **订单子智能体**: http://localhost:10006 - 处理订单相关请求
- **反馈子智能体**: http://localhost:10007 - 处理用户反馈
- **订单MCP服务器**: http://localhost:10002 - 订单管理服务
- **反馈MCP服务器**: http://localhost:10004 - 反馈管理服务
- **记忆MCP服务器**: http://localhost:10010 - 记忆管理服务

### 基础服务
- **MySQL数据库**: localhost:3306
- **Redis缓存**: localhost:6379

## 故障排除

### 常见问题

1. **Nacos无法访问**
   - 确保Docker服务正在运行
   - 等待Nacos完全启动（约1-2分钟）
   - 检查端口8848是否被占用

2. **应用启动失败**
   - 检查环境变量是否正确配置
   - 确保API密钥有效
   - 确保知识库ID已正确配置
   - 查看日志文件: `logs/` 目录

3. **知识库相关问题**
   - 确保知识库文件已成功上传到百炼
   - 检查知识库ID是否正确配置在环境变量中
   - 确保DashScope API密钥有访问知识库的权限
   - 等待知识库文档解析完成（通常需要几分钟）

4. **前端无法访问**
   - 确保Node.js版本为20+
   - 检查端口3000是否被占用
   - 重新构建前端: `cd frontend && npm run build`

5. **数据库连接失败**
   - 确保MySQL服务已启动
   - 检查数据库密码配置
   - 查看MySQL日志: `docker/middleware/mysql/log/`

### 日志查看

```bash
# 查看所有服务日志
tail -f logs/*.log

# 查看特定服务日志
tail -f logs/supervisor-agent.log
tail -f logs/frontend.log

# 查看Docker服务日志
cd docker/middleware
docker-compose logs -f nacos
docker-compose logs -f mysql
docker-compose logs -f redis
```

## 项目结构

- `consult-sub-agent/`: 咨询子智能体
- `feedback-sub-agent/`: 反馈子智能体  
- `order-sub-agent/`: 订单子智能体
- `supervisor-agent/`: 监督者智能体
- `*-mcp-server/`: MCP服务器
- `frontend/`: 前端界面
- `docker/middleware/`: 中间件服务（MySQL、Nacos、Redis）
