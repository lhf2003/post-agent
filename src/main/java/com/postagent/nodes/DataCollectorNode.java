package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.postagent.entity.PostTask;
import com.postagent.entity.PostTaskResult;
import com.postagent.repository.PostTaskResultRepository;
import com.postagent.service.PostTaskResultService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据收集节点 根据问题分析结果收集相关数据和文献
 *

 */
@Slf4j
@Component
public class DataCollectorNode implements NodeAction {

    private final RestClient restClient;
    private final PostTaskResultRepository postTaskResultRepository;

    public DataCollectorNode(RestClient.Builder restClient, PostTaskResultRepository postTaskResultRepository) {
        this.restClient = restClient.build();
        this.postTaskResultRepository = postTaskResultRepository;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        log.info("======DataCollectorNode apply start======");

        //TODO post_origin目前是是死值
        PostTask taskObject = (PostTask) state.value("task_object").orElse(new PostTask());
        String postOrigin = Optional.ofNullable(taskObject.getTargetOrigin()).orElse("小红书");

        // 解析收集结果
        Map<String, String> collectionResult = parseCollectionResult(postOrigin);

        log.info("======DataCollectorNode apply end======");

        return Map.of("collectedUrl", collectionResult.get("url"),
                "collectedTitle", collectionResult.get("title"),
                "postId", collectionResult.get("postId"));
    }

    /**
     * 获取目标参考源的帖子信息
     * @param origin 用户输入的参考源
     * @return url
     */
    public Map<String, String> parseCollectionResult(String origin) throws ResourceAccessException, NullPointerException {
        log.info("开始获取热门帖子🔎...");
        String result = "";
        List<Integer> hotPostIdList = restClient.get().uri("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty").retrieve().body(List.class);
        if (CollectionUtils.isEmpty(hotPostIdList)) {
            throw new NullPointerException("收集数据节点获取热门帖子id列表失败，返回结果为空");
        }
        Integer hotPostId = 0;
        for (Integer postId : hotPostIdList) {
            PostTaskResult postTaskResult = postTaskResultRepository.findByDataId(postId);
            if (postTaskResult != null) {
                continue;
            }
            hotPostId = postId;
            log.info("开始获取帖子id= {} 的详细信息🔎...", postId);
            result = restClient.get().uri("https://hacker-news.firebaseio.com/v0/item/" + postId + ".json?print=pretty").retrieve().body(String.class);
            break;
        }

        if (!StringUtils.hasText(result)) {
            throw new NullPointerException("收集数据节点获取数据失败，返回结果为空");
        }

        JSONObject jsonObject = JSON.parseObject(result);
        String url = jsonObject.getString("url");
        String title = jsonObject.getString("title");

        PostTaskResult saveTaskResult = new PostTaskResult();
        saveTaskResult.setDataId(Long.valueOf(hotPostId));
        saveTaskResult.setDescription(title + " 帖子url= " + url);
        postTaskResultRepository.save(saveTaskResult);

        return Map.of("url", url, "title", title, "postId", hotPostId.toString());
    }

}
