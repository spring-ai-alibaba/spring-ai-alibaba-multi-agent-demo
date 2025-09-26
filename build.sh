#!/bin/bash

# 云边奶茶铺多智能体系统启动脚本
# 启动顺序：数据库 -> MCP服务器 -> 子智能体 -> 监督者智能体

set -e  # 遇到错误立即退出

# 环境变量文件路径
ENV_FILE=".env"
ENV_TEMPLATE="env.template"
ENV_BACKUP=".env.backup"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查命令是否存在
check_command() {
    if ! command -v $1 &> /dev/null; then
        log_error "$1 命令未找到，请先安装 $1"
        exit 1
    fi
}

# 环境变量管理函数
setup_environment() {
    log_info "设置环境变量..."
    
    # 检查环境变量模板文件是否存在
    if [ ! -f "$ENV_TEMPLATE" ]; then
        log_error "环境变量模板文件 $ENV_TEMPLATE 不存在"
        exit 1
    fi
    
    # 如果.env文件不存在，从模板创建
    if [ ! -f "$ENV_FILE" ]; then
        log_info "从模板创建环境变量文件..."
        cp "$ENV_TEMPLATE" "$ENV_FILE"
        log_warning "已创建 $ENV_FILE 文件，请根据实际情况修改其中的环境变量值"
        log_warning "特别是以下关键配置："
        log_warning "  - DASHSCOPE_API_KEY: DashScope API密钥"
        log_warning "  - AI_OPENAI_API_KEY: OpenAI API密钥"
        log_warning "  - MEM0_API_KEY: Mem0 API密钥"
        log_warning "  - DB_PASSWORD: 数据库密码"
        log_warning "  - NACOS_PASSWORD: Nacos密码"
        echo ""
        read -p "是否继续启动？(y/N): " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "用户取消启动，请先配置环境变量"
            exit 0
        fi
    fi
    
    # 备份当前环境变量（如果存在）
    if [ -f "$ENV_FILE" ]; then
        cp "$ENV_FILE" "$ENV_BACKUP"
        log_info "已备份当前环境变量到 $ENV_BACKUP"
    fi
    
    # 加载环境变量
    log_info "加载环境变量..."
    set -a  # 自动导出所有变量
    source "$ENV_FILE"
    set +a  # 关闭自动导出
    
    # 环境变量已加载完成
}


# 清理环境变量
cleanup_environment() {
    log_info "清理环境变量..."
    
    # 恢复环境变量备份（如果存在）
    if [ -f "$ENV_BACKUP" ]; then
        mv "$ENV_BACKUP" "$ENV_FILE"
        log_info "已恢复环境变量备份"
    fi
    
    # 清理导出的环境变量
    unset DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD
    unset AI_OPENAI_API_KEY AI_OPENAI_BASE_URL
    unset DASHSCOPE_API_KEY DASHSCOPE_MODEL DASHSCOPE_INDEX_ID
    unset DASHSCOPE_ENABLE_RERANKING DASHSCOPE_RERANK_TOP_N DASHSCOPE_RERANK_MIN_SCORE
    unset MEM0_ADDRESS MEM0_API_KEY
    unset NACOS_SERVER_ADDR NACOS_USERNAME NACOS_PASSWORD NACOS_NAMESPACE
    unset NACOS_CLIENT_ENABLED NACOS_REGISTER_ENABLED
    unset PROMPT_KEY ADMIN_OTLP_ENDPOINT
    unset XXL_JOB_ENABLED XXL_JOB_ADMIN XXL_JOB_ACCESS_TOKEN XXL_JOB_APPNAME XXL_JOB_DINGTALK_ACCESS_TOKEN
    unset MYSQL_ROOT_PASSWORD MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD LANG
    unset NACOS_AUTH_TOKEN NACOS_AUTH_IDENTITY_KEY NACOS_AUTH_IDENTITY_VALUE MODE
    unset JVM_XMS JVM_XMX JVM_XMN
    unset REDIS_PASSWORD REDIS_DATABASE REDIS_TIMEOUT REDIS_MAX_CONNECTIONS
    unset REDIS_POOL_MAX_ACTIVE REDIS_POOL_MAX_IDLE REDIS_POOL_MIN_IDLE REDIS_POOL_MAX_WAIT
    
    log_success "环境变量清理完成"
}

# 构建前端项目
build_frontend_project() {
    local project_dir=$1
    local project_name=$2
    
    log_info "构建 $project_name..."
    cd "$project_dir"
    
    if [ ! -f "package.json" ]; then
        log_error "$project_dir 目录下没有找到 package.json 文件"
        return 1
    fi
    
    # 检查node_modules是否存在，如果不存在则安装依赖
    if [ ! -d "node_modules" ]; then
        log_info "安装前端依赖..."
        npm install
        if [ $? -ne 0 ]; then
            log_error "$project_name 依赖安装失败"
            return 1
        fi
    fi
    
    # 构建前端项目
    npm run build
    if [ $? -eq 0 ]; then
        log_success "$project_name 构建成功"
    else
        log_error "$project_name 构建失败"
        return 1
    fi
    
    cd - > /dev/null
}

# 启动前端服务
start_frontend_service() {
    local project_dir=$1
    local port=$2
    local service_name=$3
    
    log_info "启动 $service_name..."
    
    # 检查端口是否被占用
    if ! check_port $port "$service_name"; then
        return 1
    fi
    
    cd "$project_dir"
    
    # 检查package.json是否存在
    if [ ! -f "package.json" ]; then
        log_error "前端项目目录下没有找到 package.json 文件"
        return 1
    fi
    
    # 检查node_modules是否存在，如果不存在则安装依赖
    if [ ! -d "node_modules" ]; then
        log_info "安装前端依赖..."
        npm install
        if [ $? -ne 0 ]; then
            log_error "$service_name 依赖安装失败"
            return 1
        fi
    fi
    
    # 后台启动前端开发服务器
    nohup npm run dev -- --port $port --host 0.0.0.0 > "../logs/$service_name.log" 2>&1 &
    local pid=$!
    
    # 等待服务启动
    if wait_for_service $port "$service_name"; then
        log_success "$service_name 启动成功 (PID: $pid)"
        echo $pid > "../logs/$service_name.pid"
    else
        log_error "$service_name 启动失败"
        return 1
    fi
    
    cd - > /dev/null
}

# 检查端口是否被占用
check_port() {
    local port=$1
    local service_name=$2
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        log_warning "端口 $port 已被占用，$service_name 可能已经在运行"
        return 1
    fi
    return 0
}

# 等待服务启动
wait_for_service() {
    local port=$1
    local service_name=$2
    local max_attempts=30
    local attempt=0
    
    log_info "等待 $service_name 启动..."
    while [ $attempt -lt $max_attempts ]; do
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            log_success "$service_name 已启动 (端口: $port)"
            return 0
        fi
        sleep 2
        attempt=$((attempt + 1))
    done
    
    log_error "$service_name 启动超时"
    return 1
}

# 构建Maven项目
build_maven_project() {
    local project_dir=$1
    local project_name=$2
    
    log_info "构建 $project_name..."
    cd "$project_dir"
    
    if [ ! -f "pom.xml" ]; then
        log_error "$project_dir 目录下没有找到 pom.xml 文件"
        return 1
    fi
    
    mvn clean package -DskipTests
    if [ $? -eq 0 ]; then
        log_success "$project_name 构建成功"
    else
        log_error "$project_name 构建失败"
        return 1
    fi
    
    cd - > /dev/null
}

# 启动Java服务
start_java_service() {
    local project_dir=$1
    local jar_name=$2
    local port=$3
    local service_name=$4
    
    log_info "启动 $service_name..."
    
    # 检查端口是否被占用
    if ! check_port $port "$service_name"; then
        return 1
    fi
    
    cd "$project_dir"
    
    # 检查jar文件是否存在
    if [ ! -f "target/$jar_name" ]; then
        log_error "JAR文件不存在: target/$jar_name"
        return 1
    fi
    
    # 后台启动服务
    nohup java -jar "target/$jar_name" > "../logs/$service_name.log" 2>&1 &
    local pid=$!
    
    # 等待服务启动
    if wait_for_service $port "$service_name"; then
        log_success "$service_name 启动成功 (PID: $pid)"
        echo $pid > "../logs/$service_name.pid"
    else
        log_error "$service_name 启动失败"
        return 1
    fi
    
    cd - > /dev/null
}

# 主函数
main() {
    log_info "开始启动云边奶茶铺多智能体系统..."
    
    # 环境变量已在步骤2中检查和加载
    
    # 检查必要的命令
    check_command "mvn"
    check_command "java"
    check_command "lsof"
    check_command "npm"
    check_command "node"
    
    # 创建日志目录
    mkdir -p logs
    
    # 1. 检查知识库上传
    log_info "=== 步骤 1: 检查知识库上传 ==="
    log_info "在启动应用服务之前，需要将咨询子智能体的知识库文件上传到阿里云百炼知识库。"
    log_info ""
    log_info "知识库文件位于 consult-sub-agent/src/main/resources/kownledge/ 目录："
    log_info "  - brand-overview.md: 品牌概览和理念"
    log_info "  - products.md: 产品详细介绍"
    log_info ""
    log_warning "请访问阿里云百炼控制台创建知识库并上传以上文件，获取知识库ID"
    log_warning "百炼控制台地址: https://bailian.console.aliyun.com/"
    echo ""
    read -p "是否已完成知识库上传并获取了知识库ID？(y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "请先完成知识库上传后再运行此脚本"
        log_info "详细步骤请参考 README.md 中的说明"
        exit 0
    fi
    
    # 2. 检查环境变量配置
    log_info "=== 步骤 2: 检查环境变量配置 ==="
    
    # 检查.env文件是否存在
    if [ ! -f "$ENV_FILE" ]; then
        log_error ".env 文件不存在！"
        log_info "请从 env.template 复制并配置环境变量："
        log_info "  cp env.template .env"
        log_info "然后编辑 .env 文件填入正确的值"
        exit 1
    fi
    
    # 加载环境变量进行检查
    set -a
    source "$ENV_FILE"
    set +a
    
    # 检查关键环境变量
    local missing_vars=()
    if [ -z "$DASHSCOPE_API_KEY" ]; then
        missing_vars+=("DASHSCOPE_API_KEY")
    fi
    if [ -z "$DASHSCOPE_INDEX_ID" ]; then
        missing_vars+=("DASHSCOPE_INDEX_ID")
    fi
    if [ -z "$MEM0_API_KEY" ]; then
        missing_vars+=("MEM0_API_KEY")
    fi
    
    if [ ${#missing_vars[@]} -gt 0 ]; then
        log_warning "以下关键环境变量未配置："
        for var in "${missing_vars[@]}"; do
            log_warning "  - $var"
        done
        echo ""
        log_info "请编辑 .env 文件并填入正确的值"
        log_info "可以参考 env.template 文件进行配置"
        echo ""
        read -p "是否已配置完成？(y/N): " -n 1 -r
        echo ""
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "请先配置环境变量后再运行此脚本"
            exit 0
        fi
    else
        log_success "环境变量配置检查通过"
    fi
    
    # 3. 检查基础服务（MySQL + Nacos + Redis）
    log_info "=== 步骤 3: 检查基础服务（MySQL + Nacos + Redis） ==="
    log_info "请确保以下基础服务已启动："
    log_info "  - MySQL (端口: 3306)"
    log_info "  - Nacos (端口: 8848)"
    log_info "  - Redis (端口: 6379)"
    echo ""
    log_warning "重要提示：如果这是首次启动 Nacos，请先访问 http://localhost:8848/nacos"
    log_warning "使用默认账号密码（nacos/nacos）登录，然后修改密码为 nacos"
    echo ""
    read -p "基础服务是否已启动且 Nacos 账号已初始化？(y/N): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "请先启动基础服务并初始化 Nacos 账号后再运行此脚本"
        log_info "可以使用以下命令启动基础服务："
        log_info "  cd docker/middleware && docker-compose up -d mysql nacos redis"
        log_info "然后访问 http://localhost:8848/nacos 初始化账号"
        exit 0
    fi
    
    # 4. 构建和启动MCP服务器
    log_info "=== 步骤 4: 构建和启动MCP服务器 ==="
    
    # 构建feedback-mcp-server
    build_maven_project "feedback-mcp-server" "feedback-mcp-server"
    start_java_service "feedback-mcp-server" "feedback-mcp-server-1.0.0.jar" 10004 "feedback-mcp-server"
    
    # 构建order-mcp-server
    build_maven_project "order-mcp-server" "order-mcp-server"
    start_java_service "order-mcp-server" "order-mcp-server-1.0.0.jar" 10002 "order-mcp-server"
    
    # 构建memory-mcp-server
    build_maven_project "memory-mcp-server" "memory-mcp-server"
    start_java_service "memory-mcp-server" "memory-mcp-server-1.0.0.jar" 10010 "memory-mcp-server"
    
    # 5. 构建和启动子智能体
    log_info "=== 步骤 5: 构建和启动子智能体 ==="
    
    # 构建feedback-sub-agent
    build_maven_project "feedback-sub-agent" "feedback-sub-agent"
    start_java_service "feedback-sub-agent" "feedback-sub-agent-1.0.0.jar" 10007 "feedback-sub-agent"
    
    # 构建consult-sub-agent
    build_maven_project "consult-sub-agent" "consult-sub-agent"
    start_java_service "consult-sub-agent" "consult-sub-agent-1.0.0.jar" 10005 "consult-sub-agent"
    
    # 构建order-sub-agent
    build_maven_project "order-sub-agent" "order-sub-agent"
    start_java_service "order-sub-agent" "order-sub-agent-1.0.0.jar" 10006 "order-sub-agent"
    
    # 6. 构建和启动监督者智能体
    log_info "=== 步骤 6: 构建和启动监督者智能体 ==="
    
    build_maven_project "supervisor-agent" "supervisor-agent"
    start_java_service "supervisor-agent" "supervisor-agent-1.0.0.jar" 10008 "supervisor-agent"
    
    # 7. 启动前端服务
    log_info "=== 步骤 7: 启动前端服务 ==="
    
    start_frontend_service "frontend" 3000 "frontend"
    
    # 8. 显示服务状态
    log_info "=== 服务状态 ==="
    echo "基础服务状态请手动检查："
    echo "  - MySQL: localhost:3306"
    echo "  - Nacos: http://localhost:8848/nacos"
    echo "  - Redis: localhost:6379"
    
    echo ""
    echo "Java服务:"
    for service in feedback-mcp-server order-mcp-server memory-mcp-server feedback-sub-agent consult-sub-agent order-sub-agent supervisor-agent; do
        if [ -f "logs/$service.pid" ]; then
            local pid=$(cat "logs/$service.pid")
            if ps -p $pid > /dev/null 2>&1; then
                echo "  $service: 运行中 (PID: $pid)"
            else
                echo "  $service: 已停止"
            fi
        else
            echo "  $service: 未启动"
        fi
    done
    
    echo ""
    echo "前端服务:"
    if [ -f "logs/frontend.pid" ]; then
        local pid=$(cat "logs/frontend.pid")
        if ps -p $pid > /dev/null 2>&1; then
            echo "  frontend: 运行中 (PID: $pid)"
        else
            echo "  frontend: 已停止"
        fi
    else
        echo "  frontend: 未启动"
    fi
    
    echo ""
    log_success "所有服务启动完成！"
    echo ""
    echo "服务访问地址:"
    echo "  - frontend: http://localhost:3000"
    echo "  - nacos控制台: http://localhost:8848/nacos (用户名/密码: nacos/nacos)"
    echo "  - redis: localhost:6379 (密码: redis123)"
    echo "  - feedback-mcp-server: http://localhost:10004"
    echo "  - order-mcp-server: http://localhost:10002"
    echo "  - memory-mcp-server: http://localhost:10010"
    echo "  - feedback-sub-agent: http://localhost:10007"
    echo "  - consult-sub-agent: http://localhost:10005"
    echo "  - order-sub-agent: http://localhost:10006"
    echo "  - supervisor-agent: http://localhost:10008"
    echo ""
    echo "日志文件位置: logs/"
    echo "停止服务: ./stop.sh"
}

# 错误处理
trap 'log_error "脚本执行失败，请检查日志文件"; cleanup_environment; exit 1' ERR

# 执行主函数
main "$@"

