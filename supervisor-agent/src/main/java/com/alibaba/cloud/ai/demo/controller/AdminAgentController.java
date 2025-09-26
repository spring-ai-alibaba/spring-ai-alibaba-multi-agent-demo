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

package com.alibaba.cloud.ai.demo.controller;

import java.util.Map;
import java.util.concurrent.CompletionException;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/admin")
@RestController
public class AdminAgentController {

    private static final Logger logger = LoggerFactory.getLogger(AdminAgentController.class);
    private final LlmRoutingAgent adminAgent;

    public AdminAgentController(@Qualifier("adminAgentBean") LlmRoutingAgent adminAgent) {
        this.adminAgent = adminAgent;
    }

    /**
     * chat
     * curl http://localhost:10001/api/admin/chat?chat_id=1&user_id=123&user_query=每2分钟帮我统计下评价数据分析
     * @param chatID
     * @param userQuery
     * @return
     * @throws Exception
     */
    @RequestMapping(path="/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestParam(name = "chat_id") String chatID,
                                              @RequestParam(name = "user_query") String userQuery) throws Exception {

        Map<String, Object> input = Map.of("chat_id", chatID, "user_query", userQuery);
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        Flux<NodeOutput> result = adminAgent.stream(input);
        processStream(result, sink);

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }

    public void processStream(Flux<NodeOutput> generator, Sinks.Many<ServerSentEvent<String>> sink) {
        generator
                .doOnNext(output -> logger.info("output = {}", output))
                .filter(output -> "a2aNode".equals(output.node()) && output instanceof StreamingOutput)
                .cast(StreamingOutput.class)
                .map(StreamingOutput::chunk)
                .filter(content -> content != null && !content.isEmpty())
                .map(content -> ServerSentEvent.builder(content).build())
                .doOnNext(sink::tryEmitNext)
                .doOnError(e -> {
                    logger.error("Unexpected error in stream processing: {}", e.getMessage(), e);
                    sink.tryEmitNext(ServerSentEvent.builder("系统处理出现错误，请稍后重试。").build());
                })
                .doOnComplete(() -> {
                    logger.info("Stream processing completed successfully");
                    sink.tryEmitComplete();
                })
                .subscribe(
                        // onNext - 已经在doOnNext中处理
                        null,
                        // onError
                        e -> {
                            logger.error("Stream processing failed: {}", e.getMessage(), e);
                            sink.tryEmitError(e);
                        }
                );
    }

}