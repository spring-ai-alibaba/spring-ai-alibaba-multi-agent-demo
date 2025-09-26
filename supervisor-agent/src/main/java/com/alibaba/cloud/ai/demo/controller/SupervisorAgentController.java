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

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

@RequestMapping("/api/assistant/")
@RestController
public class SupervisorAgentController {

    private static final Logger logger = LoggerFactory.getLogger(SupervisorAgentController.class);
    private final LlmRoutingAgent supervisorAgent;

    public SupervisorAgentController(@Qualifier("supervisorAgentBean") LlmRoutingAgent supervisorAgent) {
        this.supervisorAgent = supervisorAgent;
    }

    @GetMapping(path="/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestParam(name = "chat_id") String chatID,
                                              @RequestParam(name = "user_query") String userQuery,
                                              @RequestParam(name = "user_id") String userID) throws Exception {
        logger.info("Received user query: {}", userQuery);

        try {
            RunnableConfig runnableConfig = RunnableConfig.builder().threadId(chatID).addMetadata("user_id", userID).build();

            String userInput = userQuery + "<userId>" + userID + "</userId>";
            Map<String, Object> input = Map.of(
                    "input", userInput, "chat_id", chatID, "user_id", userID);
            Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
            //Flux<NodeOutput> result = supervisorAgent.stream(input);

            CompiledGraph compiledGraph = supervisorAgent.getAndCompileGraph();
            Flux<NodeOutput> result = compiledGraph.fluxStream(input, runnableConfig);
            processStream(result, sink);

            return sink.asFlux()
                    .doOnCancel(() -> logger.info("Client disconnected from stream"))
                    .doOnError(e -> logger.error("Error occurred during streaming", e));
        } catch  (Exception e) {
            logger.error("Failed to process user query: {}", userQuery, e);
            return Flux.just(ServerSentEvent.builder("系统处理出现错误，请稍后重试。").build());
        }
    }

    public void processStream(Flux<NodeOutput> generator, Sinks.Many<ServerSentEvent<String>> sink) {
        generator
            .doOnNext(output -> logger.info("output = {}", output))
            .filter(output -> "a2aNode".equals(output.node()) && output instanceof StreamingOutput)
            .cast(StreamingOutput.class)
            .map(StreamingOutput::chunk)
            .filter(content -> content != null && !content.isEmpty() && !content.equals("Agent State: submitted"))
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
