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
import java.util.Map;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * DingDingMessageSenderNode is a NodeAction implementation for sending messages to DingDing webhook.
 */
public class DingMessageSenderNode implements NodeAction {

	/**
	 * The default DingDing webhook URL template with placeholder for access token
	 */
	private static final String DEFAULT_WEBHOOK_URL_TEMPLATE = "https://oapi.dingtalk.com/robot/send?access_token=%s";

	/**
	 * The access token for DingDing webhook
	 */
	private final String accessToken;

	/**
	 * The key in the state from which to retrieve the access token
	 */
	private final String accessTokenKey;

	/**
	 * The key in the state from which to retrieve the message content
	 */
	private final String messageContentKey;

	/**
	 * The key in the state where to store the result of the message sending operation
	 */
	private final String resultKey;

	/**
	 * The title of the DingDing message
	 */
	private final String title;

	/**
	 * Custom webhook URL (optional, if not using default with access token)
	 */
	private final String customWebhookUrl;

	/**
	 * Constructor for DingDingMessageSenderNode
	 *
	 * @param accessToken       the access token for DingDing webhook
	 * @param messageContentKey the key in the state from which to retrieve the message content
	 * @param resultKey         the key in the state where to store the result
	 * @param title             the title of the DingDing message
	 */
	public DingMessageSenderNode(String accessToken, String accessTokenKey, String messageContentKey, String resultKey, String title) {
		this(accessToken, accessTokenKey, messageContentKey, resultKey, title, null);
	}

	/**
	 * Constructor for DingDingMessageSenderNode with custom webhook URL
	 *
	 * @param accessToken       the access token for DingDing webhook
	 * @param messageContentKey the key in the state from which to retrieve the message content
	 * @param resultKey         the key in the state where to store the result
	 * @param title             the title of the DingDing message
	 * @param customWebhookUrl  custom webhook URL (optional)
	 */
	public DingMessageSenderNode(String accessToken, String accessTokenKey, String messageContentKey, String resultKey, String title, String customWebhookUrl) {
		this.accessToken = accessToken;
		this.messageContentKey = messageContentKey;
		this.resultKey = resultKey;
		this.title = title;
		this.customWebhookUrl = customWebhookUrl;
		this.accessTokenKey = accessTokenKey;
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		Object message  = state.value(messageContentKey).orElse(null);
		String messageContent;
		if (message instanceof AssistantMessage) {
			messageContent = ((AssistantMessage) message).getText();
		} else {
			messageContent = (String) message;
		}
		if (!StringUtils.hasLength(messageContent)) {
			String errorMsg = "Message content is empty or not found in state with key: " + messageContentKey;
			return Map.of(resultKey, errorMsg);
		}
		Object accessToken = this.accessToken;
		if (StringUtils.hasText(accessTokenKey)) {
			accessToken = state.value(accessTokenKey).orElse(this.accessToken);
		}
		try {
			String response = sendMessage(accessToken.toString(), messageContent);
			return Map.of(resultKey, response);
		} catch (Exception e) {
			String errorMsg = "Failed to send DingDing message: " + e.getMessage();
			return Map.of(resultKey, errorMsg);
		}
	}

	/**
	 * Send message to DingDing webhook
	 *
	 * @param messageContent the content of the message to send
	 * @return the response from the DingDing webhook
	 * @throws JsonProcessingException if there's an error processing JSON
	 */
	private String sendMessage(String accessToken, String messageContent) throws JsonProcessingException {
		String webhookUrl = StringUtils.hasLength(customWebhookUrl) ? customWebhookUrl : String.format(DEFAULT_WEBHOOK_URL_TEMPLATE, accessToken);

		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		Map<String, Object> requestBody = createRequestBody(messageContent);
		String requestBodyJson = new ObjectMapper().writeValueAsString(requestBody);

		HttpEntity<String> request = new HttpEntity<>(requestBodyJson, headers);
		ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

		return response.getBody();
	}

	/**
	 * Create the request body for the DingDing webhook
	 *
	 * @param messageContent the content of the message
	 * @return the request body as a Map
	 */
	private Map<String, Object> createRequestBody(String messageContent) {
		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("msgtype", "markdown");

		Map<String, String> markdown = new HashMap<>();
		markdown.put("title", title);
		markdown.put("text", messageContent);
		requestBody.put("markdown", markdown);

		return requestBody;
	}

	/**
	 * Builder class for DingDingMessageSenderNode
	 */
	public static class Builder {
		private String accessToken;
		private String accessTokenKey;
		private String messageContentKey;
		private String resultKey = "dingding_message_result";
		private String title = "Notification";
		private String customWebhookUrl;

		public Builder accessToken(String accessToken) {
			this.accessToken = accessToken;
			return this;
		}

		public Builder accessTokenKey(String accessTokenKey) {
			this.accessTokenKey = accessTokenKey;
			return this;
		}

		public Builder messageContentKey(String messageContentKey) {
			this.messageContentKey = messageContentKey;
			return this;
		}

		public Builder resultKey(String resultKey) {
			this.resultKey = resultKey;
			return this;
		}

		public Builder title(String title) {
			this.title = title;
			return this;
		}

		public Builder customWebhookUrl(String customWebhookUrl) {
			this.customWebhookUrl = customWebhookUrl;
			return this;
		}

		public DingMessageSenderNode build() {
			return new DingMessageSenderNode(accessToken, accessTokenKey, messageContentKey, resultKey, title, customWebhookUrl);
		}
	}

	/**
	 * Create a new builder for DingDingMessageSenderNode
	 *
	 * @return a new Builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}
}