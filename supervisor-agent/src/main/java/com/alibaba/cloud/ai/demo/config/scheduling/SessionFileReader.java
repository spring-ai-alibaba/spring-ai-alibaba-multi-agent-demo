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

package com.alibaba.cloud.ai.demo.config.scheduling;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

/**
 * SessionFileReader用于读取sessions.txt文件并按分隔符将内容存储到List中
 */
public class SessionFileReader {

    /**
     * 读取sessions.txt文件，按============================================分隔符将每个会话存储到List中
     *
     * @return 包含每个会话内容的List
     * @throws IOException 文件读取异常
     */
    public static List<String> readSessionsFromFile(String filePath) throws IOException {
        List<String> sessions = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(filePath);
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            StringBuilder currentSession = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("============================================")) {
                    // 遇到分隔符，将当前会话添加到列表中（如果不为空）
                    if (currentSession.length() > 0) {
                        // 移除末尾可能的换行符
                        String session = currentSession.toString().trim();
                        if (!session.isEmpty()) {
                            sessions.add(session);
                        }
                        currentSession = new StringBuilder();
                    }
                } else {
                    // 添加当前行到会话内容中
                    currentSession.append(line).append("\n");
                }
            }
            
            // 处理最后一个会话（因为文件末尾没有分隔符）
            if (currentSession.length() > 0) {
                String session = currentSession.toString().trim();
                if (!session.isEmpty()) {
                    sessions.add(session);
                }
            }
        }
        return sessions;
    }
}