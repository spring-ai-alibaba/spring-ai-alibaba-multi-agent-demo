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

package com.alibaba.cloud.ai.demo.controller;

import com.alibaba.cloud.ai.demo.service.MemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
@CrossOrigin(origins = "*")
public class MemoryController {
    
    @Autowired
    private MemoryService memoryService;
    
    /**
     * 存储用户记忆 - 异步处理
     */
    @PostMapping("/store")
    public ResponseEntity<Map<String, Object>> storeMemory(@RequestBody StoreMemoryRequest request) {
        Map<String, Object> response = new HashMap<>();
        // 异步存储记忆，立即返回成功状态
        String result = memoryService.storeMemory(request.getUserId(), request.getContent());
        response.put("success", true);
        response.put("message", result);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 搜索用户历史记忆 - 同步版本
     */
    @GetMapping("/search/{userId}")
    public ResponseEntity<Map<String, Object>> searchMemory(@PathVariable String userId, @RequestParam(required = true) String query) {
        Map<String, Object> response = new HashMap<>();
        String result = memoryService.searchMemory(userId, query);
        response.put("success", true);
        response.put("data", result);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 存储记忆请求DTO
     */
    public static class StoreMemoryRequest {
        private String userId;
        private String content;
        
        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
