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
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class FeedbackAgent {
    private static final Logger logger = LoggerFactory.getLogger(FeedbackAgent.class);

	@Autowired
	private FeedbackAgentPromptConfig promptConfig;

    ToolCallbackProvider toolsProvider;

    @Bean
    public ReactAgent feedbackSubAgentBean(//@Qualifier("openAiChatModel") ChatModel chatModel,
										   @Qualifier("dashscopeChatModel") ChatModel chatModel,
										   @Autowired(required = false)
										   @Qualifier("loadbalancedMcpSyncToolCallbacks")
										   ToolCallbackProvider toolsProvider) throws Exception {
		this.toolsProvider = toolsProvider;

		KeyStrategyFactory stateFactory = () -> {
			HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
			keyStrategyHashMap.put("messages", new ReplaceStrategy());
			return keyStrategyHashMap;
		};

		List<ToolCallback> tools = new ArrayList<>();
		for (ToolCallback toolCallback : toolsProvider.getToolCallbacks()) {
			String toolName = toolCallback.getToolDefinition().name();
			logger.info("feedback_agent add tool: " + toolName);
			tools.add(toolCallback);
		}
		logger.info("feedback_agent add tools: " + tools.size());
		return ReactAgent.builder()
				.name("feedback_agent")
				.model(chatModel)
				.state(stateFactory)
				.description("用户反馈相关业务处理，支持从反馈中提取和记录用户偏好")
				.instruction(promptConfig.getFeedbackAgentInstruction())
				.inputKey("messages")
				.outputKey("messages")
				.tools(tools)
				.build();
	}
}
