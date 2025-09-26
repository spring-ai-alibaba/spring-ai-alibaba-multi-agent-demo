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

import com.alibaba.cloud.ai.agent.nacos.NacosAgentPromptBuilderFactory;
import com.alibaba.cloud.ai.agent.nacos.NacosOptions;
import com.alibaba.cloud.ai.demo.tools.ConsultTools;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

@Configuration
public class ConsultAgent {
    private static final Logger logger = LoggerFactory.getLogger(ConsultAgent.class);

	@Autowired
	private AgentPromptConfig promptConfig;

	@Autowired
	private ConsultTools consultTools;

	NacosOptions nacosOptions;

    ToolCallbackProvider toolsProvider;

	public ConsultAgent(NacosOptions nacosOptions) {
		this.nacosOptions = nacosOptions;
	}

    @Bean
    public ReactAgent consultSubAgentBean(//@Qualifier("openAiChatModel") ChatModel chatModel,
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

		// add tools from mcp servers
        List<ToolCallback> tools = new ArrayList<>();
		for (ToolCallback toolCallback : toolsProvider.getToolCallbacks()) {
			String toolName = toolCallback.getToolDefinition().name();
			logger.info("consult_agent add mcp tool name: " + toolName);
			tools.add(toolCallback);
		}

		// add local tools
		MethodToolCallbackProvider localToolsProvider = MethodToolCallbackProvider.builder()
				.toolObjects(consultTools)
				.build();
		for (ToolCallback toolCallback : localToolsProvider.getToolCallbacks()) {
			logger.info("consult_agent add local tool name: " + toolCallback.getToolDefinition().name());
			tools.add(toolCallback);
		}

		logger.info("consult_agent add tools: " + tools.size());
		logger.info("nacos options info: " + nacosOptions.toString());

		return ReactAgent
				//.builder(new NacosAgentPromptBuilderFactory(nacosOptions))
				.builder()
				.name("consult_agent")
				.model(chatModel)
				.state(stateFactory)
				.description("处理奶茶相关产品、活动等咨询问题，支持基于用户记忆的个性化推荐")
				.instruction(promptConfig.getConsultAgentInstruction())
				.inputKey("messages")
				.outputKey("messages")
				.tools(tools)
				.build();
	}
}
