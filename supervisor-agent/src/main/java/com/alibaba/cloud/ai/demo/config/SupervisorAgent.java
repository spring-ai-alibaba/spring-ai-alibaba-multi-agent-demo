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

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;

@Configuration
public class SupervisorAgent {
    private static final Logger logger = LoggerFactory.getLogger(SupervisorAgent.class);

    @Autowired
    private SupervisorAgentPromptConfig promptConfig;

    @Bean
    public LlmRoutingAgent supervisorAgentBean(ChatModel chatModel,
                                               @Autowired
                                               //@Qualifier("nacosAgentCardProvider")
                                               AgentCardProvider agentCardProvider) throws Exception {
        logger.info("agent card provider: {}", agentCardProvider);

        KeyStrategyFactory stateFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            keyStrategyHashMap.put("input", new ReplaceStrategy());
            keyStrategyHashMap.put("chat_id", new ReplaceStrategy());
            keyStrategyHashMap.put("user_id", new ReplaceStrategy());
            keyStrategyHashMap.put("messages", new ReplaceStrategy());
            return keyStrategyHashMap;
        };

        AgentCard consultAgentCard = agentCardProvider.getAgentCard("consult_agent").getAgentCard();
        if (consultAgentCard != null) {
            logger.info("consult agent card info: {}", consultAgentCard);
        } else {
            logger.warn("consult agent card not found!");
        }
        A2aRemoteAgent consultAgent = A2aRemoteAgent.builder()
                .name("consult_agent")
                .agentCardProvider(agentCardProvider)
                .description("处理奶茶相关产品、活动等咨询问题")
                .build();

        AgentCard feedbackAgentCard = agentCardProvider.getAgentCard("feedback_agent").getAgentCard();
        if (feedbackAgentCard != null) {
            logger.info("feedback agent card info: {}", feedbackAgentCard);
        } else {
            logger.warn("feedback agent card not found!");
        }
        A2aRemoteAgent feedbackAgent = A2aRemoteAgent.builder()
                .name("feedback_agent")
                .agentCardProvider(agentCardProvider)
                .description("云边奶茶铺反馈处理助手")
                .build();

        AgentCard orderAgentCard = agentCardProvider.getAgentCard("order_agent").getAgentCard();
        if (orderAgentCard != null) {
            logger.info("order agent card info: {}", orderAgentCard);
        } else {
            logger.warn("order agent card not found!");
        }
        A2aRemoteAgent orderAgent = A2aRemoteAgent.builder()
                .name("order_agent")
                .agentCardProvider(agentCardProvider)
                .description("云边奶茶铺智能订单处理助手")
                .build();

        logger.info("supervisor_agent initialized with A2A client service");
        
        try {
            return LlmRoutingAgent.builder()
                    .name("supervisor_agent")
                    .model(chatModel)
                    .state(stateFactory)
                    .description(promptConfig.getSupervisorAgentInstruction())
                    .inputKey("input")
                    .outputKey("messages")
                    .subAgents(List.of(consultAgent, feedbackAgent, orderAgent))
                    .build();
        } catch (Exception e) {
            logger.error("Failed to create LlmRoutingAgent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize supervisor agent", e);
        }
    }
}
