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

package com.alibaba.cloud.ai.demo.service;

import com.alibaba.cloud.ai.demo.config.Mem0Config;
import com.alibaba.cloud.ai.demo.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MemoryService {
    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);
    private static final String MEMORIES_URI_V1 = "/v1/memories/";
    private static final String MEMORIES_URI_V2 = "/v2/memories/search/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Mem0Config config;
    private final ApplicationContext applicationContext;

    @Autowired
    public MemoryService(RestTemplate restTemplate, Mem0Config config, ApplicationContext applicationContext) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.config = config;
        this.applicationContext = applicationContext;
    }

    public String searchMemory(String userId, String query) {
        try {
            // 计算时间范围：今天的两周前到明天
            LocalDate today = LocalDate.now();
            LocalDate twoWeeksAgo = today.minusWeeks(2);
            LocalDate tomorrow = today.plusDays(1);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String startDate = twoWeeksAgo.format(formatter);
            String endDate = tomorrow.format(formatter);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> filters = new HashMap<>();
            List<Map<String, Object>> andConditions = new ArrayList<>();

            // 添加用户ID条件
            Map<String, Object> userIdCondition = new HashMap<>();
            userIdCondition.put("user_id", userId);
            andConditions.add(userIdCondition);

            // 添加时间范围条件
            Map<String, Object> timeCondition = new HashMap<>();
            Map<String, String> createdAtRange = new HashMap<>();
            createdAtRange.put("gte", startDate);
            createdAtRange.put("lte", endDate);
            timeCondition.put("created_at", createdAtRange);
            andConditions.add(timeCondition);

            filters.put("AND", andConditions);
            requestBody.put("filters", filters);
            requestBody.put("query", query);

            String requestJson = objectMapper.writeValueAsString(requestBody);
            logger.info("Sending memory search request: {}", requestJson);

            // 使用 RestTemplate 进行同步调用，避免在 reactive 上下文中使用 block()
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Token " + config.getApi().getKey());
            HttpEntity<String> requestEntity = new HttpEntity<>(requestJson, headers);

            String url = config.getApi().getUrl() + MEMORIES_URI_V2;
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, requestEntity, String.class);
            String response = responseEntity.getBody();

            // 解析响应
            List<Map<String, Object>> memories = objectMapper.readValue(response, new TypeReference<List<Map<String, Object>>>() {});

            if (!memories.isEmpty()) {
                StringBuilder result = new StringBuilder();

                for (int i = 0; i < memories.size(); i++) {
                    Map<String, Object> memory = memories.get(i);
                    result.append(memory.get("memory"));

                    // 如果不是最后一个记忆，添加换行符
                    if (i < memories.size() - 1) {
                        result.append("\n");
                    }
                }
                logger.info("Found {} memories for user: {} in date range {} to {}", memories.size(), userId, startDate, endDate);
                return result.toString();
            } else {
                logger.warn("No memories found for user: {} in date range {} to {}", userId, startDate, endDate);
                return "未找到用户历史喜好";
            }
        } catch (Exception e) {
            logger.error("Error searching memories for user: {}", userId, e);
            return "未找到用户历史喜好";
        }
    }

    /**
     * 存储用户记忆 - 异步方法，立即返回成功状态
     */
    public String storeMemory(String userId, String content) {
        // 立即返回成功状态
        logger.info("Memory storage request received for user: {}, content: {}", userId, content);
        
        // 通过ApplicationContext获取代理对象来调用异步方法
        MemoryService self = applicationContext.getBean(MemoryService.class);
        self.storeMemoryAsync(userId, content);

        return "成功存储用户喜好";
    }
    
    /**
     * 异步存储用户记忆 - 后台执行
     */
    @Async("memoryTaskExecutor")
    public void storeMemoryAsync(String userId, String content) {
        try {
            logger.info("Starting async memory storage for user: {}", userId);
            
            // 创建消息
            Message message = new Message("user", content);
            List<Message> messages = Arrays.asList(message);
            
            // 使用 Builder 模式创建请求
            Mem0ServerRequest.MemoryCreate memoryCreate = Mem0ServerRequest.MemoryCreate.builder()
                    .messages(messages)
                    .userId(userId != null && !userId.trim().isEmpty() ? userId : "default_user")
                    .build();

            String requestJson = objectMapper.writeValueAsString(memoryCreate);
            logger.info("Sending async memory request: {}", requestJson);

            // 使用 RestTemplate 进行同步调用，避免在异步方法中使用阻塞操作
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Token " + config.getApi().getKey());
            HttpEntity<String> requestEntity = new HttpEntity<>(requestJson, headers);
            
            String url = config.getApi().getUrl() + MEMORIES_URI_V1;
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, requestEntity, String.class);
            String response = responseEntity.getBody();

            if (response != null) {
                logger.info("Successfully added memory with {} messages for user: {}", memoryCreate.getMessages().size(), userId);
                logger.debug("Memory creation response: {}", response);
            }
            
            logger.info("Async memory storage completed successfully for user: {}", userId);
        } catch (Exception e) {
            logger.error("Error in async memory storage for user: {}", userId, e);
        }
    }
}