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

package com.alibaba.cloud.ai.demo.tools;

import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.scheduling.ScheduleConfig;
import com.alibaba.cloud.ai.graph.scheduling.ScheduledAgentTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * CronAgentTools
 * @author yaohui
 * @create 2025/7/30 22:51
 **/
@Component
public class CronAgentTools {

	private static final Logger logger = LoggerFactory.getLogger(CronAgentTools.class);

	@Autowired(required = false)
	private Map<String, CompiledGraph> agentsMap;

	@Tool(description = "可根据用户提供的定时表达式, 创建运行相应的Agent在后台定时执行")
	public String createCronAgent(@ToolParam( description = "Cron expression for scheduling (e.g., '0 0 8 * * ?' for daily at 8 AM，need 6 parameters)") String cron,
								  @ToolParam( description = "Agent bean ame in current spring context")String agentName) {
		logger.info("Getting information for {}", cron);
		System.out.println("创建了一个 " + cron+ " 的定时Agent。Name:" + agentName);
		if (agentsMap == null) {
			System.out.println("Agent not found");
			return "Agent not found";
		}
		CompiledGraph agent = agentsMap.get(agentName);
		if (agent == null) {
			System.out.println("Agent not found");
			return "Agent not found";
		}
		ScheduleConfig config = ScheduleConfig.builder().cronExpression(cron).build();
		ScheduledAgentTask task = agent.schedule(config);
		return "成功创建了一个 " + cron+ " 的定时Agent。" + agent.stateGraph.getName();
	}

	public List<String> cronAgentsDesc() {
		if (agentsMap == null) {
			return List.of();
		}
		return agentsMap.entrySet().stream()
				.map(entry -> "AgentName: " + entry.getKey() + ", Function Describe: " + entry.getValue().stateGraph.getName())
				.toList();
	}
}
