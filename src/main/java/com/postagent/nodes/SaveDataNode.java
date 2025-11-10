package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.postagent.entity.PostTask;
import com.postagent.repository.PostTaskRepository;
import jakarta.annotation.Resource;

import java.util.Date;
import java.util.Map;

/**
 * 保存数据节点 用于保存任务数据到数据库
 */
public class SaveDataNode implements NodeAction {
    @Resource
    private PostTaskRepository postTaskRepository;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object taskObject = state.value("task_object").get();
        PostTask postTask = JSON.parseObject(taskObject.toString(), PostTask.class);
        postTask.setStatus(PostTask.Status.SUCCESS.getValue());
        postTask.setCreateTime(new Date());
        postTask.setTargetOrigin(state.value("target_origin").get().toString());
        postTaskRepository.save(postTask);
        return Map.of();
    }
}