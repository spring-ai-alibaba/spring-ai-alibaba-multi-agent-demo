#!/bin/bash

# 云边奶茶铺多智能体系统停止脚本

set -e

# 环境变量文件路径
ENV_FILE=".env"
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

# 清理环境变量
cleanup_environment() {
    log_info "清理环境变量..."
    
    # 恢复环境变量备份（如果存在）
    if [ -f "$ENV_BACKUP" ]; then
        mv "$ENV_BACKUP" "$ENV_FILE"
        log_info "已恢复环境变量备份"
    fi
    
    # 清理导出的环境变量（只清理.env文件中定义的环境变量）
    if [ -f "$ENV_FILE" ]; then
        # 提取.env文件中定义的环境变量名（排除注释和空行）
        local defined_vars=$(grep -v '^#' "$ENV_FILE" | grep -v '^$' | grep '=' | cut -d'=' -f1 | tr -d ' ')
        
        # 清理每个定义的环境变量
        for var in $defined_vars; do
            unset "$var"
        done
        
        log_info "已清理 $ENV_FILE 中定义的环境变量"
    fi
    
    log_success "环境变量清理完成"
}

# 停止Java服务
stop_java_service() {
    local service_name=$1
    local pid_file="logs/$service_name.pid"
    
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p $pid > /dev/null 2>&1; then
            log_info "停止 $service_name (PID: $pid)..."
            kill $pid
            sleep 2
            
            # 检查进程是否已停止
            if ps -p $pid > /dev/null 2>&1; then
                log_warning "$service_name 未正常停止，强制终止..."
                kill -9 $pid
                sleep 1
                
                # 再次检查
                if ps -p $pid > /dev/null 2>&1; then
                    log_error "$service_name 无法停止"
                    return 1
                fi
            fi
            
            log_success "$service_name 已停止"
        else
            log_warning "$service_name 进程不存在"
        fi
        rm -f "$pid_file"
    else
        log_warning "$service_name PID文件不存在"
    fi
}

# 强制停止所有Java服务
force_stop_java_services() {
    log_info "强制停止所有Java服务..."
    
    # 查找所有相关的Java进程
    local pids=$(ps aux | grep java | grep -E "(feedback|order|consult|supervisor|memory)" | grep -v grep | awk '{print $2}')
    
    if [ -n "$pids" ]; then
        log_info "发现Java进程: $pids"
        for pid in $pids; do
            log_info "强制停止进程 $pid..."
            kill -9 $pid 2>/dev/null
        done
        log_success "所有Java进程已强制停止"
    else
        log_info "没有发现需要停止的Java进程"
    fi
}

# 强制停止前端服务
force_stop_frontend_service() {
    log_info "强制停止前端服务..."
    
    # 查找前端相关的Node.js进程
    local pids=$(ps aux | grep node | grep -E "(vite|preview)" | grep -v grep | awk '{print $2}')
    
    if [ -n "$pids" ]; then
        log_info "发现前端进程: $pids"
        for pid in $pids; do
            log_info "强制停止前端进程 $pid..."
            kill -9 $pid 2>/dev/null
        done
        log_success "前端进程已强制停止"
    else
        log_info "没有发现需要停止的前端进程"
    fi
}

# 提示用户手动停止基础服务
stop_docker_services() {
    log_info "基础服务（MySQL + Nacos + Redis）需要手动停止"
    log_info "可以使用以下命令停止基础服务："
    log_info "  cd docker/middleware && docker-compose down"
}

# 主函数
main() {
    log_info "开始停止云边奶茶铺多智能体系统..."
    
    # 停止前端服务（最先停止）
    log_info "=== 停止前端服务 ==="
    stop_java_service "frontend"
    
    # 停止Java服务（按启动顺序的逆序）
    log_info "=== 停止Java服务 ==="
    stop_java_service "supervisor-agent"
    stop_java_service "order-sub-agent"
    stop_java_service "consult-sub-agent"
    stop_java_service "feedback-sub-agent"
    stop_java_service "memory-mcp-server"
    stop_java_service "order-mcp-server"
    stop_java_service "feedback-mcp-server"
    
    # 检查是否还有前端进程在运行
    local remaining_frontend_pids=$(ps aux | grep node | grep -E "(vite|preview)" | grep -v grep | awk '{print $2}')
    if [ -n "$remaining_frontend_pids" ]; then
        log_warning "发现仍有前端进程在运行，执行强制停止..."
        force_stop_frontend_service
    fi
    
    # 检查是否还有Java进程在运行
    local remaining_pids=$(ps aux | grep java | grep -E "(feedback|order|consult|supervisor|memory)" | grep -v grep | awk '{print $2}')
    if [ -n "$remaining_pids" ]; then
        log_warning "发现仍有Java进程在运行，执行强制停止..."
        force_stop_java_services
    fi
    
    # 提示停止基础服务
    log_info "=== 基础服务停止提示 ==="
    stop_docker_services
    
    # 清理PID文件
    log_info "清理PID文件..."
    rm -f logs/*.pid
    
    # 清理环境变量
    cleanup_environment
    
    log_success "所有服务已停止"
}

# 执行主函数
main "$@"
