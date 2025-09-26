/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.demo;

import com.alibaba.cloud.ai.demo.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MemoryMcpTools {
    private static final Logger logger = LoggerFactory.getLogger(MemoryMcpTools.class);

    @Autowired
    private MemoryService memoryService;
    
    /**
     * 存储用户记忆
     */
    @Tool(name = "memory-store", description = "存储用户的多维度偏好和习惯信息，包括产品偏好、口味偏好、服务偏好、消费习惯、情绪反馈等，为个性化推荐、智能咨询、订单处理提供基础数据支持")
    public String storeMemory(
            @ToolParam(description = "用户唯一标识符，用于关联用户的所有记忆信息") String userId,
            @ToolParam(description = "用户偏好和习惯的详细描述，包括：产品偏好（喜欢/不喜欢的产品）、口味偏好（甜度、冰量、口味）、服务偏好（配送、包装、服务态度）、消费习惯（价格敏感度、促销响应、下单时间）、情绪反馈（满意度、投诉内容、建议）等") String content ) {
        return memoryService.storeMemory(userId, content);
    }
    
    /**
     * 搜索用户历史记忆
     */
    @Tool(name = "memory-search", description = "检索用户的历史偏好、习惯和反馈信息，支持个性化推荐、智能咨询、订单处理等场景，可查询产品偏好、口味偏好、服务偏好、消费习惯、情绪反馈等多维度信息")
    public String searchMemory(@ToolParam(description = "用户唯一标识符，用于检索该用户的所有记忆信息") String userId,
                               @ToolParam(description = "检索查询语句，可以是具体的偏好类型（如'甜度偏好'、'产品偏好'）、产品名称（如'奶茶'、'咖啡'）、行为模式（如'下单习惯'、'消费习惯'）或情感关键词（如'喜欢'、'不喜欢'）") String query) {
        return memoryService.searchMemory(userId, query);
    }
}
