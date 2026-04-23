package io.rankpeek.service;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 自动化任务基类
 * 提供任务管理和调度功能
 */
@Slf4j
public abstract class BaseAutomationTask {

    protected final ScheduledExecutorService scheduler;
    protected final Map<String, ScheduledFuture<?>> tasks;

    protected BaseAutomationTask(ScheduledExecutorService scheduler, Map<String, ScheduledFuture<?>> tasks) {
        this.scheduler = scheduler;
        this.tasks = tasks;
    }

    /**
     * 启动任务
     */
    protected void startTask(String taskName, Runnable taskLogic, long initialDelay, long period, TimeUnit unit) {
        stopTask(taskName);
        log.info("启动任务：{}", taskName);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(taskLogic, initialDelay, period, unit);
        tasks.put(taskName, future);
    }

    /**
     * 停止任务
     */
    protected void stopTask(String taskName) {
        ScheduledFuture<?> task = tasks.remove(taskName);
        if (task != null) {
            task.cancel(false);
            log.info("已停止任务：{}", taskName);
        }
    }

    /**
     * 获取任务状态
     */
    public boolean isTaskRunning(String taskName) {
        ScheduledFuture<?> task = tasks.get(taskName);
        return task != null && !task.isCancelled() && !task.isDone();
    }

    /**
     * 停止所有任务
     */
    public void stopAllTasks() {
        tasks.keySet().forEach(this::stopTask);
    }
}
