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

package com.alibaba.cloud.ai.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Mem0 服务器请求类
 */
public class Mem0ServerRequest implements Serializable {
    
    /**
     * 创建记忆请求
     */
    public static class MemoryCreate implements Serializable {
        
        private List<Message> messages;
        
        @JsonProperty("user_id")
        private String userId;
        
        @JsonProperty("agent_id")
        private String agentId;
        
        @JsonProperty("run_id")
        private String runId;
        
        private Map<String, Object> metadata;
        
        public MemoryCreate() {}
        
        private MemoryCreate(Builder builder) {
            setMessages(builder.messages);
            setUserId(builder.userId);
            setAgentId(builder.agentId);
            setRunId(builder.runId);
            setMetadata(builder.metadata);
        }
        
        public List<Message> getMessages() {
            return messages;
        }
        
        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public String getAgentId() {
            return agentId;
        }
        
        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }
        
        public String getRunId() {
            return runId;
        }
        
        public void setRunId(String runId) {
            this.runId = runId;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static final class Builder {
            
            private List<Message> messages;
            private String userId;
            private String agentId;
            private String runId;
            private Map<String, Object> metadata;
            
            private Builder() {}
            
            public static Builder builder() {
                return new Builder();
            }
            
            public Builder messages(List<Message> val) {
                messages = val;
                return this;
            }
            
            public Builder userId(String val) {
                userId = val;
                return this;
            }
            
            public Builder agentId(String val) {
                agentId = val;
                return this;
            }
            
            public Builder runId(String val) {
                runId = val;
                return this;
            }
            
            public Builder metadata(Map<String, Object> val) {
                metadata = val;
                return this;
            }
            
            public MemoryCreate build() {
                return new MemoryCreate(this);
            }
        }
    }
}
