package com.postagent.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.postagent.entity.PostTask;
import com.postagent.entity.PostTaskResult;
import com.postagent.repository.PostTaskRepository;
import com.postagent.repository.PostTaskResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PostTaskService {
    @Autowired
    private PostTaskRepository postTaskRepository;
    @Autowired
    private PostTaskResultRepository postTaskResultRepository;

    @Autowired
    @Qualifier("compiledPostAgentGraph")
    private CompiledGraph compiledGraph;

    public void addPostTask(PostTask postTask) {
        // 保存任务到数据库
        postTask.setCreateTime(new Date());
        postTask.setStatus(PostTask.Status.PENDING.getValue());
        PostTask save = postTaskRepository.save(postTask);
        if (save == null) {
            throw new RuntimeException("添加任务失败");
        }
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
        Optional<OverAllState> result = compiledGraph.call(params);
        if (result.isPresent()) {
            OverAllState overallState = result.get();
            postTask.setStatus(PostTask.Status.SUCCESS.getValue());
            postTaskRepository.save(postTask);
            Long postId = Long.parseLong(overallState.value("postId").get().toString());
            PostTaskResult postTaskResult = postTaskResultRepository.findByDataId(postId);
            postTaskResult.setOutputDirectory(overallState.value("targetDir").get().toString());
            postTaskResultRepository.save(postTaskResult);
        } else {
            postTask.setStatus(PostTask.Status.FAILED.getValue());
        }
    }
}