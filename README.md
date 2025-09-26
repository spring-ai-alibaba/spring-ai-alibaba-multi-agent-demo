# spring-ai-alibaba-multi-agent-demo
Cloud-Side Beverage Shop Smart Order Assistant - A distributed multi-agent system built using Spring AI Alibaba Agentic API

## 项目简介
### 功能介绍
云边奶茶铺智能助手Demo, 支持一站式咨询、点单与反馈，持续根据用户行为和喜好推荐并下单产品, 从而实现"越来越懂我", "越用越好用"的用户体验。

Demo的用户端能力主要包括:
1. 产品咨询与产品推荐。根据用户习惯和喜好, 为用户推荐奶茶产品并介绍, 同时分析并记录用户习惯和喜好。
2. 点单与订单查询。根据用户需求下订单、修改订单和查询订单, 同时分析并记录用户习惯和喜好。
3. 反馈与投诉处理。处理用户反馈, 对于投诉或差评安抚情绪并出解决方案, 同时分析并记录用户习惯和喜好。

Demo的管理端能力主要包括: 定时分析用户消费和反馈数据, 并总结生成报告, 用于指导后续产品运营。

### 服务架构
![服务架构](https://img.alicdn.com/imgextra/i4/O1CN01vctYqQ22cB0D4BDlF_!!6000000007140-2-tps-5298-1406.png)

### 项目结构
- `frontend/`: 前端界面
- `supervisor-agent/`: 监督者智能体
- `consult-sub-agent/`: 咨询子智能体
- `feedback-sub-agent/`: 反馈子智能体
- `order-sub-agent/`: 订单子智能体
- `*-mcp-server/`: MCP服务器
- `docker/middleware/`: 中间件服务（MySQL、Nacos、Redis）

### 环境要求

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
docker-compose up -d

# 等待服务启动完成（约30-60秒）
# 可以通过以下命令查看服务状态
docker-compose ps
```

### 步骤 4: 初始化 Nacos 控制台

在启动应用服务之前，必须先初始化Nacos控制台账号密码。
- 打开浏览器访问: http://localhost:8848/nacos
- 初始化Nacos账号和密码, 用户名: `nacos`, 密码: `nacos`

### 步骤 5: 启动应用服务

完成Nacos初始化后，返回项目根目录启动应用服务：

```bash
# 启动所有应用服务, 包括mcp server、agent和前端页面
./build.sh
```

## 停止服务

```bash
# 停止所有应用服务, 包括mcp server、agent和前端页面
./stop.sh

# 停止基础存储和中间件依赖（可选）
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
   - 重新构建前端: `cd frontend && npm run dev`

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

## 其他说明

**请注意：** 当前Demo版本默认**没有集成**以下组件，需要用户根据实际需求手动部署：

#### Higress 网关
- **当前状态**: Demo中未包含Higress网关配置
- **如需集成**: 用户需要自行部署Higress网关，并通过简单的配置修改将服务路由到网关
- **集成步骤**: 
  1. 部署Higress网关服务, 并在网关配置LLM路由或MCP Server
  2. 将LLM地址替换为Higress路由: 将Agent使用的ChatModel由dashscopeChatModel替换为openAiChatModel, 同时在application.yml中spring.ai.openai.base-url设置网关地址
  3. 将order-service mcp地址改为Higress代理的mcp server: 在application.yml中注释spring.ai.alibaba.mcp.nacos.client.sse.connections中order-mcp-server相关内容, 同时取消spring.ai.mcp.client.sse.connections中order-service相关注释, 并正确配置网关Mcp Server地址

#### Spring AI Alibaba Admin
- **当前状态**: Demo中未包含Spring AI Alibaba Admin管理控制台
- **如需集成**: 用户需要单独部署Admin控制台，用于监控和管理AI智能体
- **集成步骤**:
  1. 部署Spring AI Alibaba Admin服务, 参考[Spring AI Alibaba Admin](https://github.com/spring-ai-alibaba/spring-ai-alibaba-admin/blob/main/README-zh.md)
  2. 智能体服务接入Admin, 参考参考[Spring AI Alibaba Admin](https://github.com/spring-ai-alibaba/spring-ai-alibaba-admin/blob/main/README-zh.md)
  3. 通过Admin控制台进行智能体的监控、配置和管理

