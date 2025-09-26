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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.demo.entity.Feedback;
import com.alibaba.cloud.ai.demo.mapper.FeedbackMapper;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.node.IterationNode;
import com.alibaba.cloud.ai.graph.node.LlmNode;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.google.gson.Gson;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.util.GsonTool;
import org.apache.commons.lang3.StringUtils;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * CronTaskConfiguration
 * @author yaohui
 * @create 2025/8/15 15:37
 **/
@Configuration
public class EvaluationAgentConfiguration {

	@Value("${agent.dingtalk.access-token}")
	private String accessToken;

	@Bean
	public CompiledGraph evaluationAnalysisAgent(ChatModel chatModel,
					 FeedbackMapper feedbackMapper) throws GraphStateException {

		ChatClient chatClient = ChatClient.builder(chatModel).defaultAdvisors(new SimpleLoggerAdvisor()).build();

		// 配置子图：START -> iterator -> END
		KeyStrategyFactory subFactory1 = () -> {
			Map<String, KeyStrategy> map = new HashMap<>();
			map.put("iterator_item", new ReplaceStrategy());
			map.put("session_analysis_result", new ReplaceStrategy());
			return map;
		};

		EvaluationClassifierNode sessionAnalysis = EvaluationClassifierNode.builder()
				.chatClient(chatClient)
				.inputTextKey("iterator_item")
				.outputKey("session_analysis_result")
				.categories(List.of("yes", "no"))
				.classificationInstructions(
						List.of("结果仅需返回JSON字符串，不能有其他不符合JSON格式字符出现，包含字段:user、time、complaint、satisfaction、summary。",
								"complaint: 表示当前评价是否为店铺或产品投诉，取值范围（yes or no）.",
								"satisfaction: 表示用户实际的消费满意度",
								"summary: 提炼本条核心吐槽点，以及可以改进的方向"))
				.build();

		StateGraph sessionAnalysisGraph = new StateGraph("session_analysis", subFactory1)
				.addNode("iterator", node_async(sessionAnalysis))
				.addEdge(StateGraph.START, "iterator")
				.addEdge("iterator", StateGraph.END);

		AsyncNodeAction sessionLoaderNode = node_async(
				(state) -> {
					XxlJobContext xxlJobContext = (XxlJobContext)state.value("xxl-job-context").orElse( null);
					String maxMonth = feedbackMapper.selectMaxCreatedMonth();
					Date startTime;
					Date endTime;
					
					if (maxMonth != null && !maxMonth.isEmpty()) {
						// Parse the maxMonth string (format: "yyyy-MM") to create the first day of that month
						try {
							YearMonth yearMonth = YearMonth.parse(maxMonth);
							LocalDate firstDayOfMonth = yearMonth.atDay(1);
							// Convert to Date objects
							startTime = Date.from(firstDayOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant());
						} catch (Exception e) {
							// Fallback to default behavior if parsing fails
							startTime = new Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000); // One year ago
						}
					} else {
						// Fallback to default behavior if maxMonth is null or empty
						startTime = new Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000); // One year ago
					}
					endTime = new Date();

					List<Feedback> list = feedbackMapper.selectByTimeRange(startTime, endTime);
					List<String> sessionList = list.stream().map(Feedback::toFormattedString).toList();

					Map<String, Object> result = new HashMap<>();
					result.put("sessions", sessionList);
					if (xxlJobContext != null) {
						try {
							String accessToken = GsonTool.fromJson(xxlJobContext.getJobParam(), Map.class).get("access_token").toString();
							result.put("access_token", accessToken);
						}
						catch (Exception e) {
							System.out.println("解析任务参数失败: " + e.getMessage());
						}
					}
					return result;
				}
		);

		// 返回数据解析节点
		AsyncNodeAction sessionResultSummaryNode = node_async(
				(state) -> {
					String s = state.value("analysis_results", "[]");
					String message = """
						用户投诉分析监控
						总评价记录数: %d条，产品投诉: %d条, 平均满意度(0～5): %d.
						用户核心诉求：%s
						""";
					List<String> results = new Gson().fromJson(s, List.class);
					int total = results.size();
					int complaint = 0;
					int satisfaction = 0;
					String require = "";
					if (!results.isEmpty()) {
						for (String result:results) {
							Map<String, Object> map = new Gson().fromJson(result, Map.class);
							if (StringUtils.equals("yes", map.get("complaint").toString())) {
								complaint++;
								if (map.containsKey("summary") && map.get("summary") instanceof String) {
									require += map.get("summary") + "\n";
								}
							}
							if (map.containsKey("satisfaction") && map.get("satisfaction") instanceof Number) {
								satisfaction += ((Number) map.get("satisfaction")).intValue();
							}
						}
						message = String.format(message, total, complaint, (satisfaction/total), require);
						System.out.println(">>" + message);
						return Map.of("summary_message", Map.of("context", message));
					}
					return Map.of();
				}
		);

		LlmNode llmNode = LlmNode.builder().chatClient(chatClient)
				.paramsKey("summary_message")
				.outputKey("summary_message_to_sender")
				.systemPromptTemplate("""
					你是一个告警信息整理助手，需要将用户提供的信息整体适合钉钉发送的Markdown格式，对用户核心诉求后的内容进行压缩提炼总结核心点。
					
					核心内容如下：{context}
					
					约束：
					用户提供内容中的数据数值信息绝对不能串改，改进方向如果有则确保控制在3条以内，信息结构内容参考如下格式
					## 📊 用户投诉分析监控

					**📈 总评价记录数**：`%d` 条 \s
					**⚠️ 产品投诉**：`%d` 条 \s
					**⭐ 平均满意度 (0～5)**：`%d`
			
					---
			
					### 🔍 用户核心诉求
					> %s
					
					---
					
					### 🛠️ 改进方向
					
				""")
				.build();

		StateGraph iterationNode = IterationNode.converter()
				.inputArrayJsonKey("sessions")
				.tempIndexKey("iteration_index1")
				.outputArrayJsonKey("analysis_results")
				.iteratorItemKey("iterator_item")
				.iteratorResultKey("session_analysis_result")
				.tempArrayKey("test_temp_array1")
				.tempStartFlagKey("test_temp_start1")
				.tempEndFlagKey("test_temp_end1")
				.subGraph(sessionAnalysisGraph)
				.convertToStateGraph();


		StateGraph stateGraph = new StateGraph("ReviewAnalysisAgent", () -> {
			Map<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("iterator_item", new ReplaceStrategy());
			strategies.put("session_analysis_result", new ReplaceStrategy());
			strategies.put("sessions", new ReplaceStrategy());
			strategies.put("iteration_index1", new ReplaceStrategy());
			strategies.put("analysis_results", new ReplaceStrategy());
			strategies.put("test_temp_array1", new ReplaceStrategy());
			strategies.put("test_temp_start1", new ReplaceStrategy());
			strategies.put("test_temp_end1", new ReplaceStrategy());
			strategies.put("summary_message", new ReplaceStrategy());
			strategies.put("summary_message_to_sender", new ReplaceStrategy());
			strategies.put("message_sender_result", new ReplaceStrategy());
			strategies.put("access_token", new ReplaceStrategy());
			return strategies;
		}).addNode("session_loader_node", sessionLoaderNode)
				.addNode("iteration_session_analysis_node", iterationNode)
				.addNode("session_result_summary_node", sessionResultSummaryNode)
				.addNode("message_parse", node_async(llmNode))
				.addNode("message_sender", node_async(generateMessageSender()))
				.addEdge(START, "session_loader_node")
				.addEdge("session_loader_node", "iteration_session_analysis_node")
				.addEdge("iteration_session_analysis_node", "session_result_summary_node")
				.addEdge("session_result_summary_node", "message_parse")
				.addEdge("message_parse", "message_sender")
				.addEdge("message_sender", END);

		CompiledGraph compiledGraph = stateGraph.compile();
		compiledGraph.setMaxIterations(1000);
		return compiledGraph;
	}

	private DingMessageSenderNode generateMessageSender() {
		String messageContentKey = "summary_message_to_sender";
		String resultKey = "message_sender_result";
		String title = "用户投诉分析监控";
		return DingMessageSenderNode.builder()
				.accessToken(accessToken)
				.accessTokenKey("access_token")
				.messageContentKey(messageContentKey)
				.resultKey(resultKey)
				.title(title)
				.build();
	}
}