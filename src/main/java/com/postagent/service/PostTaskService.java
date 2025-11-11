package com.postagent.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.postagent.entity.PostTask;
import com.postagent.entity.PostTaskResult;
import com.postagent.repository.PostTaskRepository;
import com.postagent.repository.PostTaskResultRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PostTaskService {
    @Resource
    private PostTaskRepository postTaskRepository;
    @Resource
    private PostTaskResultRepository postTaskResultRepository;
    @Resource(name = "compiledPostAgentGraph")
    private CompiledGraph compiledGraph;

    public void addPostTask(PostTask postTask) {
        // 保存任务到数据库
        postTask.setCreateTime(new Date());
        postTask.setStatus(PostTask.Status.PENDING.getValue());
        postTaskRepository.save(postTask);
    }

    public List<PostTask> getPostTasksByPage(Pageable pageable) {
        return postTaskRepository.findByPage(pageable);
    }

    public void executePostTask(Long taskId) {
        // 从数据库查询任务
        PostTask postTask = postTaskRepository.findById(taskId).orElse(null);
        if (postTask == null) {
            throw new RuntimeException("任务不存在");
        }
        postTask.setStatus(PostTask.Status.RUNNING.getValue());
        Map<String, Object> params = Map.of("task_object", postTask);
        postTaskRepository.save(postTask);
        // 执行工作流
        Optional<OverAllState> result = compiledGraph.call(params);
        if (result.isPresent()) {
            OverAllState overallState = result.get();
            // 更新任务状态
            postTask.setStatus(PostTask.Status.SUCCESS.getValue());
            postTaskRepository.save(postTask);

            // 解析工作流结果
            String collectedTitle = overallState.value("collectedTitle").orElseThrow(() -> new RuntimeException("collectedTitle 不存在")).toString();
            String url = overallState.value("collectedUrl").orElseThrow(() -> new RuntimeException("collectedUrl 不存在")).toString();
            String targetDir = overallState.value("targetDir").orElseThrow(() -> new RuntimeException("targetDir 不存在")).toString();
            String postId = overallState.value("postId").orElseThrow(() -> new RuntimeException("postId 不存在")).toString();

            // 保存任务结果
            PostTaskResult postTaskResult = new PostTaskResult();
            postTaskResult.setDataId(Long.valueOf(postId));
            postTaskResult.setStatus(PostTask.Status.SUCCESS.getValue());
            postTaskResult.setDescription(collectedTitle + " 帖子url= " + url);
            postTaskResult.setOutputDirectory(targetDir);
            postTaskResultRepository.save(postTaskResult);
        } else {
            postTask.setStatus(PostTask.Status.FAILED.getValue());
        }
    }
}