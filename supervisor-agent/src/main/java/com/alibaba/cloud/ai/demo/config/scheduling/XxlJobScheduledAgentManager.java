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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.cloud.ai.graph.scheduling.DefaultScheduledAgentManager;
import com.alibaba.cloud.ai.graph.scheduling.ScheduledAgentManager;
import com.alibaba.cloud.ai.graph.scheduling.ScheduledAgentTask;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.handler.IJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

/**
 * XxlJobScheduledAgentManager
 * @author yaohui
 * @create 2025/9/15 11:33
 **/
public class XxlJobScheduledAgentManager implements ScheduledAgentManager {

	private static final Logger log = LoggerFactory.getLogger(DefaultScheduledAgentManager.class);

	private static final TaskScheduler taskScheduler = new ConcurrentTaskScheduler();

	private final Map<String, ScheduledAgentTask> activeTasks = new ConcurrentHashMap<>();

	private volatile boolean shutdown = false;

	@Override
	public String registerTask(ScheduledAgentTask task) {
		XxlJobExecutor.registJobHandler(task.getName(), new IJobHandler() {
			@Override
			public void execute() throws Exception {
				XxlJobContext context = XxlJobContext.getXxlJobContext();
				Map<String, Object> inputs = Map.of("xxl-job-context", context);
				task.execute(null, inputs);
			}
		});
		activeTasks.put(task.getName(), task);
		return task.getName();
	}

	@Override
	public boolean unregisterTask(String taskId) {
		activeTasks.remove(taskId);
		return true;
	}

	@Override
	public Optional<ScheduledAgentTask> getTask(String taskId) {
		return Optional.ofNullable(activeTasks.get(taskId));
	}

	@Override
	public Set<String> getAllActiveTaskIds() {
		return Set.of();
	}

	@Override
	public int getActiveTaskCount() {
		return 0;
	}

	@Override
	public TaskScheduler getTaskScheduler() {
		return taskScheduler;
	}

	@Override
	public boolean isShutdown() {
		return shutdown;
	}

	@Override
	public void shutdown() {
		activeTasks.clear();
	}
}
