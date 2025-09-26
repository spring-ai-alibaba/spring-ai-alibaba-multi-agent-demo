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

import java.util.List;

import com.alibaba.cloud.ai.demo.config.SupervisorAgentPromptConfig;
import com.alibaba.cloud.ai.demo.tools.CronAgentTools;
import com.alibaba.cloud.ai.graph.agent.BaseAgent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * CronAgentConfiguration
 * @author yaohui
 * @create 2025/9/3 11:46
 **/
@Configuration
public class CronAgentConfiguration {

	@Autowired
	private SupervisorAgentPromptConfig promptConfig;

	@Bean
	public BaseAgent cronTaskParseAgent(CronAgentTools cronAgentTools, ChatModel chatModel) throws GraphStateException {
		String agentNames = "";
		for (String desc : cronAgentTools.cronAgentsDesc()) {
			agentNames += "- " + desc + "\n";
		}
		String instruction = String.format(promptConfig.getSchedulingAgentInstruction(), agentNames);
		ReactAgent cronTaskParseAgent = ReactAgent.builder()
				.name("CronTaskParseAgent")
				.model(chatModel)
				.description("CronTaskParseAgent可按用户提供的定时或周期性执行指令，帮助用户创建一个异步定时运行的Agent.")
				.instruction(instruction)
				.inputKey("agent_input")
				.outputKey("messages")
				.tools(List.of(ToolCallbacks.from(cronAgentTools)))
				.build();
		return cronTaskParseAgent;
	}
}
