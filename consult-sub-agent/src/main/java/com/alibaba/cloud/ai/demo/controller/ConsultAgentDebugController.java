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

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

@RequestMapping("/api/consult_sub_agent/")
@RestController
public class ConsultAgentDebugController {

    private static final Logger logger = LoggerFactory.getLogger(ConsultAgentDebugController.class);
    private final ReactAgent consultSubAgent;

    public ConsultAgentDebugController(@Qualifier("consultSubAgentBean") ReactAgent consultSubAgent) {
        this.consultSubAgent = consultSubAgent;
    }

    @RequestMapping(path="/debug", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestParam(name = "user_query") String userQuery) throws Exception {

        Map<String, Object> input = Map.of(
                "messages", List.of(new UserMessage(userQuery)));
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();
        Flux<NodeOutput> result = consultSubAgent.stream(input);
        processStream(result, sink);

        logger.info("agent instruction: {}, description: {}", consultSubAgent.instruction(), consultSubAgent.description());
        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .doOnError(e -> logger.error("Error occurred during streaming", e));
    }

    public void processStream(Flux<NodeOutput> generator, Sinks.Many<ServerSentEvent<String>> sink) {
        generator
            .doOnNext(output -> logger.info("output = {}", output))
            .filter(output -> "llm".equals(output.node()) && output instanceof StreamingOutput)
            .cast(StreamingOutput.class)
            .filter(streamingOutput -> {
                String chunk = streamingOutput.chunk();
                return chunk != null && !chunk.trim().isEmpty();
            })
            .map(StreamingOutput::chunk)
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