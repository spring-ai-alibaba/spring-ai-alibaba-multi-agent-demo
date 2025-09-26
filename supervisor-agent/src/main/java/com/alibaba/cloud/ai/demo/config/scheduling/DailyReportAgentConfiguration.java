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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.demo.entity.Feedback;
import com.alibaba.cloud.ai.demo.entity.Order;
import com.alibaba.cloud.ai.demo.entity.Product;
import com.alibaba.cloud.ai.demo.mapper.FeedbackMapper;
import com.alibaba.cloud.ai.demo.mapper.OrderMapper;
import com.alibaba.cloud.ai.demo.mapper.ProductMapper;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.node.LlmNode;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.util.GsonTool;

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
public class DailyReportAgentConfiguration {

	@Value("${agent.dingtalk.access-token}")
	private String accessToken;

	private static final String DAILY_REPORT = """
			你是一个经营日报助手，能根据用户提供的核心数据信息进行分析总结，并按指定格式生成对应报告。
			
			用户提供的数据内容如下：
			{content}
			
			约束
			在如下的报告格式中，相关产品数据金额部分的描述不允许进行修改调整，在如下返回内容中对应的"<该区域待分析替换></该区域待分析替换>"位置，参考其指引方向进行总结分析并完整替换"<该区域待分析替换></该区域待分析替换>"的内容。
			
			返回内容限定如下：
			
			# 🏪 门店经营日报 \s
			> 日期：{report_date} \s
			> 店铺名称：**{store_name}** \s
			> 报告生成时间：{report_time}
			
			---
			
			## 1. 📦 上一日经营概览
			- **总销量（件）**：{total_sales} \s
			- **总销售额（¥）**：{total_revenue} \s
			- **平均客单价（¥）**：{avg_price} \s
			- **环比昨日**：{sales_growth}（销售额） / {order_change}（订单数）
			
			---
			
			## 2. 🏆 TOP3 热销产品
			
			- **销量榜**
			
			1. 🥇 **{product1}** - {product1_quantity}杯（占总销量 {product1_percentage}%） \s
			2. 🥈 **{product2}** - {product2_quantity}杯（占总销量 {product2_percentage}%） \s
			3. 🥉 **{product3}** - {product3_quantity}杯（占总销量 {product3_percentage}%） \s
			
			- **营收榜**
			
			4. 🥇 **{r_product1}** - {r_product1_quantity}元（占总营收 {r_product1_percentage}%）
			5. 🥈 **{r_product2}** - {r_product2_quantity}元（占总营收 {r_product2_percentage}%）
			6. 🥉 **{r_product3}** - {r_product3_quantity}元（占总营收 {r_product3_percentage}%）
			
			> 🔍 **洞察**：<该区域待分析替换>根据用户提供信息中的产品销量和销售额TOP3结合产品说明，分析该区域用户的产品喜好偏向，通过营收和销量关系分析出该区域适合的产品定位</该区域待分析替换>
			
			---
			
			## 3. ⭐ 口碑表现
			- 好评率：{positive_rate}  👍 \s
			- 差评率：{negative_rate} 👎 \s
			- 中评率：{neutral_rate}
			
			📊 **评分分布（5分制）**：
			
			★★★★★ {star5_rate}%
			★★★★ {star4_rate}%
			★★★ {star3_rate}%
			★★ {star2_rate}%
			★ {star1_rate}%
			
			> 💡 **洞察**：<该区域待分析替换>根据用户评价分析主要的产品相关重点问题是什么</该区域待分析替换>
			
			---
			
			## 4. 💬 客户核心诉求 & 意见反馈
			- **强烈诉求**：
			  <该区域待分析替换>根据用户评价分析主要的产品相关重点问题是什么，控制在3条内，参考返回格式： - 提前备货、减少高峰等待时间 - 控制热饮温度（避免过烫）</该区域待分析替换>
			- **精选客户留言**：
			  <该区域待分析替换>根据用户评价分析选取两条有助于改善经营的评价意见，控制在3条内，参考返回格式： - “下午排队太久，希望增加人手。” - “拿铁味道不错，但温度有点高。”</该区域待分析替换>
			
			---
			
			## 5. 📈 门店运营建议（改进方向）
			<该区域待分析替换>根据当前返回内容上述信息，按市场经营做出优化改进分析，分模块给出当前门店优化方向，控制在4条以内，参考返回格式如下：1. **高峰期排队优化**：context。 2. **产品结构优化**：context。</该区域待分析替换>
			
			---
			
			📌 **备注**：本日报由【智能营运分析系统】自动生成，数据来源 订单 + 客户评价。
			""";


	@Bean
	public CompiledGraph dailyReportAgent(ChatModel chatModel,
												 FeedbackMapper feedbackMapper,
												 OrderMapper orderMapper,
												 ProductMapper productMapper
	) throws GraphStateException {

		ChatClient chatClient = ChatClient.builder(chatModel).defaultAdvisors(new SimpleLoggerAdvisor()).build();

		AsyncNodeAction dataLoaderNode = node_async(
				(state) -> {
					XxlJobContext xxlJobContext = (XxlJobContext)state.value("xxl-job-context").orElse( null);
					int shardIndex = 0;
					if (xxlJobContext != null) {
						shardIndex = xxlJobContext.getShardIndex();
					}
					// 模拟测试数据，直接按当前测试数据最大时间来获取
					String maxMonth = orderMapper.selectMaxCreatedMonth();
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

					
					String content = "";
					List<Feedback> list = feedbackMapper.selectByTimeRange(startTime, endTime);
					List<String> feedbacks = list.stream().map(Feedback::toFormattedString).toList();
					// 计算好评差评中评比例，5分属于好评，4～3分属于中评价，3分以下属于差评
					// Filter out feedbacks with null ratings
					List<Feedback> validFeedbacks = list.stream()
							.filter(f -> f.getRating() != null)
							.collect(Collectors.toList());
					content += "用户评价反馈信息：\n" + feedbacks.stream().collect(Collectors.joining("\n"));
					
					// Calculate review statistics
					int totalValidFeedbacks = validFeedbacks.size();
					long positiveCount = validFeedbacks.stream().filter(f -> f.getRating() == 5).count();
					long neutralCount = validFeedbacks.stream().filter(f -> f.getRating() >= 3 && f.getRating() <= 4).count();
					long negativeCount = validFeedbacks.stream().filter(f -> f.getRating() < 3).count();
					
					// Calculate percentages
					double positiveRate = totalValidFeedbacks > 0 ? (positiveCount * 100.0 / totalValidFeedbacks) : 0;
					double neutralRate = totalValidFeedbacks > 0 ? (neutralCount * 100.0 / totalValidFeedbacks) : 0;
					double negativeRate = totalValidFeedbacks > 0 ? (negativeCount * 100.0 / totalValidFeedbacks) : 0;
					
					// Calculate rating distribution (1-5 stars)
					long[] ratingDistribution = new long[5];
					for (int i = 0; i < 5; i++) {
						final int rating = i + 1;
						ratingDistribution[i] = validFeedbacks.stream().filter(f -> f.getRating() != null && f.getRating() == rating).count();
					}
					
					// Calculate percentage distribution
					double[] ratingPercentage = new double[5];
					for (int i = 0; i < 5; i++) {
						ratingPercentage[i] = totalValidFeedbacks > 0 ? (ratingDistribution[i] * 100.0 / totalValidFeedbacks) : 0;
					}


					List<Order> todayOrders = orderMapper.findOrdersByTimeRange(startTime, endTime);
					int todayOrderCount = todayOrders.size();
					BigDecimal totalRevenue = todayOrders.stream().map(Order::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

					Date yesterdayStartTime = new Date(startTime.getTime() - (365L * 24 * 60 * 60 * 1000)); // One year ago
					Date yesterdayEndTime = startTime;
					List<Order> yesterdayOrders = orderMapper.findOrdersByTimeRange(yesterdayStartTime, yesterdayEndTime);
					int yesterdayOrderCount = yesterdayOrders.size();
					BigDecimal yesterdayTotalRevenue = yesterdayOrders.stream().map(Order::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);

					// 分类计算今日销量
					Map<Long, Integer> productSalesCountMap = todayOrders.stream().collect(Collectors.groupingBy(Order::getProductId,
							Collectors.summingInt(Order::getQuantity)));
					Map<Long, BigDecimal> productSalesRevenueMap = todayOrders.stream().collect(Collectors.groupingBy(Order::getProductId,
							Collectors.reducing(BigDecimal.ZERO, Order::getTotalPrice, BigDecimal::add)));
					// 找出销量最大的前3个产品
					List<Map.Entry<Long, Integer>> top3BySalesCount = productSalesCountMap.entrySet().stream()
							.sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
							.limit(3)
							.collect(Collectors.toList());
					
					// 找出销售额最大的前3个产品
					List<Map.Entry<Long, BigDecimal>> top3ByRevenue = productSalesRevenueMap.entrySet().stream()
							.sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
							.limit(3)
							.collect(Collectors.toList());
					
					// Prepare data for template variables
					Map<String, Object> templateData = new HashMap<>();
					templateData.put("store_name", "云原生"+(shardIndex+1)+"号门店");
					templateData.put("feedbacks", feedbacks);
					templateData.put("total_sales", todayOrderCount);
					templateData.put("yesterday_total_sales", yesterdayOrderCount);
					templateData.put("total_revenue", String.format("%.2f", totalRevenue));
					templateData.put("avg_price", totalRevenue.divide(new BigDecimal(todayOrderCount), 2, RoundingMode.HALF_UP).doubleValue());

					templateData.put("sales_growth", String.format((totalRevenue.doubleValue() - yesterdayTotalRevenue.doubleValue()>=0)?"📈":"📉"+" %.2f",
							(totalRevenue.doubleValue() - yesterdayTotalRevenue.doubleValue()) / yesterdayTotalRevenue.doubleValue() * 100) + "%");
					templateData.put("order_change", String.format((todayOrderCount - yesterdayOrderCount>=0)?"📈":"📉"+"%.2f",
							(((double)todayOrderCount - (double)yesterdayOrderCount) / (double)yesterdayOrderCount * 100D)) + "%");
					
					// Add review statistics
					templateData.put("positive_rate", String.format("%.0f", positiveRate) + "%");
					templateData.put("neutral_rate", String.format("%.0f", neutralRate) + "%");
					templateData.put("negative_rate", String.format("%.0f", negativeRate) + "%");

					// Format date and time in yyyy-MM-dd HH:mm:ss format
					DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
					DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
					templateData.put("report_date", LocalDate.now().format(dateFormatter));
					templateData.put("report_time", LocalDate.now().format(dateFormatter) + " " + LocalTime.now().format(timeFormatter));
					
					// Add rating distribution
					for (int i = 0; i < 5; i++) {
						templateData.put("star" + (i + 1) + "_rate", String.format("%.0f", ratingPercentage[i]));
					}
					
					// Add top 3 products by sales count
					content += "\n产品销量说明：\n" ;
					for (int i = 0; i < 3; i++) {
						if (i < top3ByRevenue.size()) {
							Map.Entry<Long, BigDecimal> entry = top3ByRevenue.get(i);
							// Get product name from productMapper or use a default name
							String productName = "Product " + entry.getKey();
							Product product = null;
							try {
								// Try to get the actual product name
								product = productMapper.selectById(entry.getKey());
								if (product != null && product.getName() != null) {
									productName = product.getName();
								}
							} catch (Exception e) {
								// Use default name if product not found
							}
							templateData.put("r_product" + (i + 1), productName);
							templateData.put("r_product" + (i + 1) + "_quantity", String.format("%.2f", entry.getValue()));
							// Calculate percentage of total sales
							double percentage = (entry.getValue().doubleValue() * 100.0) / totalRevenue.doubleValue();
							templateData.put("r_product" + (i + 1) + "_percentage", String.format("%.1f", percentage));

							content += productName + " 销售额排名第" + (i + 1) + "，销售额为 " + String.format("%.2f", entry.getValue()) + "，占比为 " + String.format("%.1f", percentage)
									+ "%, 产品单价："+ (product != null ? product.getPrice() : "")
									+ ", 产品描述："+ (product != null ? product.getDescription() : "") +"\n" ;
						} else {
							templateData.put("r_product" + (i + 1), "N/A");
							templateData.put("r_product" + (i + 1) + "_quantity", 0);
							templateData.put("r_product" + (i + 1) + "_percentage", "0.0");
						}
					}

					for (int i = 0; i < 3; i++) {
						if (i < top3BySalesCount.size()) {
							Map.Entry<Long, Integer> entry = top3BySalesCount.get(i);
							// Get product name from productMapper or use a default name
							String productName = "Product " + entry.getKey();
							Product product = null;
							try {
								// Try to get the actual product name
								product = productMapper.selectById(entry.getKey());
								if (product != null && product.getName() != null) {
									productName = product.getName();
								}
							} catch (Exception e) {
								// Use default name if product not found
							}
							templateData.put("product" + (i + 1), productName);
							templateData.put("product" + (i + 1) + "_quantity", entry.getValue());
							// Calculate percentage of total sales
							double percentage = (entry.getValue() * 100.0) / todayOrderCount;
							templateData.put("product" + (i + 1) + "_percentage", String.format("%.1f", percentage));
							content += productName + " 销售量排名第" + (i + 1) + "，销量为 " + entry.getValue() + "，占比为 " + String.format("%.1f", percentage) + "%, 产品描述："+ (product != null ? product.getDescription() : "") +"\n" ;
						} else {
							templateData.put("product" + (i + 1), "N/A");
							templateData.put("product" + (i + 1) + "_quantity", 0);
							templateData.put("product" + (i + 1) + "_percentage", "0.0");
						}
					}
					templateData.put("content", content);

					Map<String, Object> result = new HashMap<>();
					result.put("data_summary", templateData);
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


		LlmNode llmDataAnalysisNode = LlmNode.builder().chatClient(chatClient)
				.paramsKey("data_summary")
				.outputKey("summary_message_to_sender")
				.userPromptTemplate(DAILY_REPORT)
				.build();

		StateGraph stateGraph = new StateGraph("OperationAnalysisAgent", () -> {
			Map<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("data_summary", new ReplaceStrategy());
			strategies.put("summary_message_to_sender", new ReplaceStrategy());
			strategies.put("message_sender_result", new ReplaceStrategy());
			strategies.put("access_token", new ReplaceStrategy());
			return strategies;
		}).addNode("data_loader", dataLoaderNode)
				.addNode("data_analysis", node_async(llmDataAnalysisNode))
				.addNode("message_sender", node_async(generateMessageSender()))
				.addEdge(START, "data_loader")
				.addEdge("data_loader", "data_analysis")
				.addEdge("data_analysis", "message_sender")
				.addEdge("message_sender", END);

		CompiledGraph compiledGraph = stateGraph.compile();
		compiledGraph.setMaxIterations(100);
		return compiledGraph;
	}

	private DingMessageSenderNode generateMessageSender() {
		String messageContentKey = "summary_message_to_sender";
		String resultKey = "message_sender_result";
		String title = "门店经营日报";
		return DingMessageSenderNode.builder()
				.accessToken(accessToken)
				.accessTokenKey("access_token")
				.messageContentKey(messageContentKey)
				.resultKey(resultKey)
				.title(title)
				.build();
	}
}