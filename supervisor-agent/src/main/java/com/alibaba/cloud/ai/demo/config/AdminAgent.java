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

package com.alibaba.cloud.ai.demo.config;

import java.util.HashMap;
import java.util.List;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.agent.BaseAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminAgent {

    private static final Logger logger = LoggerFactory.getLogger(AdminAgent.class);

    @Autowired
    private SupervisorAgentPromptConfig promptConfig;

    @Bean
    public LlmRoutingAgent adminAgentBean(ChatModel chatModel,
			@Qualifier("cronTaskParseAgent") BaseAgent cronTaskParseAgent) throws Exception {
        KeyStrategyFactory stateFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            keyStrategyHashMap.put("user_query", new ReplaceStrategy());
            keyStrategyHashMap.put("chat_id", new ReplaceStrategy());
            keyStrategyHashMap.put("user_id", new ReplaceStrategy());
            keyStrategyHashMap.put("result", new ReplaceStrategy());
            return keyStrategyHashMap;
        };
        return LlmRoutingAgent.builder()
                .name("admin_agent")
                .model(chatModel)
                .state(stateFactory)
                .description(promptConfig.getSupervisorAgentInstruction())
                .inputKey("user_query")
                .outputKey("agent_input")
                .subAgents(List.of(cronTaskParseAgent))
                .build();
    }
}
