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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.util.StringUtils;

public class EvaluationClassifierNode implements NodeAction {

	private static final String CLASSIFIER_PROMPT_TEMPLATE = """
				### Job Description
				你是一个用户评价智能分析智能助手.
				### Task
				对用户的评价记录，你需要完成以下两项信息分析：
				1、判断是否为产品投诉：根据评价内容分析判断是否为原料质量或店员失误导致的产品投诉。
				2、客户情绪状态分析：从客户回复语气判断客户满意度，如果存在情绪激动、抱怨、不耐烦等情况，情绪越强烈分值越低，分值范围：0～5.
				### Format
				The conversation is: {inputText}. Categories are specified as a category list: {categories}. Satisfaction value is number from 0 to 5.
				Classification instructions may be included to improve the classification accuracy: {classificationInstructions}.
				### Constraint
				输出JSON字符串，不要包含markdown相关字符。DO NOT include anything other than the JSON string in your response. 输出信息参考如下：
				\\{'user':'10000', 'time': '2025-09-02 14:15:42', 'complaint':'yes', 'satisfaction':1, 'summary':'产品问题'\\}.
			""";

	private SystemPromptTemplate systemPromptTemplate;

	private ChatClient chatClient;

	private String inputText;

	private List<String> categories;

	private List<String> classificationInstructions;

	private String inputTextKey;

	private String outputKey;

	public EvaluationClassifierNode(ChatClient chatClient, String inputTextKey, List<String> categories,
									List<String> classificationInstructions, String outputKey) {
		this.chatClient = chatClient;
		this.inputTextKey = inputTextKey;
		this.categories = categories;
		this.classificationInstructions = classificationInstructions;
		this.systemPromptTemplate = new SystemPromptTemplate(CLASSIFIER_PROMPT_TEMPLATE);
		this.outputKey = outputKey;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		if (StringUtils.hasLength(inputTextKey)) {
			this.inputText = (String) state.value(inputTextKey).orElse(this.inputText);
		}

		ChatResponse response = chatClient.prompt()
			.system(systemPromptTemplate.render(Map.of("inputText", inputText, "categories", categories,
					"classificationInstructions", classificationInstructions)))
			.user(inputText)
			.call()
			.chatResponse();

		Map<String, Object> updatedState = new HashMap<>();
		updatedState.put(outputKey, response.getResult().getOutput().getText());
		System.out.println(">>"+response.getResult().getOutput().getText());
		if (state.value("messages").isPresent()) {
			updatedState.put("messages", response.getResult().getOutput());
		}

		return updatedState;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String inputTextKey;

		private ChatClient chatClient;

		private List<String> categories;

		private List<String> classificationInstructions;

		private String outputKey;

		public Builder inputTextKey(String input) {
			this.inputTextKey = input;
			return this;
		}

		public Builder chatClient(ChatClient chatClient) {
			this.chatClient = chatClient;
			return this;
		}

		public Builder categories(List<String> categories) {
			this.categories = categories;
			return this;
		}

		public Builder classificationInstructions(List<String> classificationInstructions) {
			this.classificationInstructions = classificationInstructions;
			return this;
		}

		public Builder outputKey(String outputKey) {
			this.outputKey = outputKey;
			return this;
		}

		public EvaluationClassifierNode build() {
			return new EvaluationClassifierNode(chatClient, inputTextKey, categories, classificationInstructions,
					outputKey);
		}

	}

}
