package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.postagent.entity.PostTask;
import com.postagent.entity.PostTaskResult;
import com.postagent.repository.PostTaskResultRepository;
import lombok.extern.slf4j.Slf4j;
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
        String postOrigin = Optional.ofNullable(taskObject.getTargetOrigin()).orElse("HackerNews");

        // 解析收集结果
        Map<String, String> collectionResult = parseCollectionResult(postOrigin);

        log.info("✅收集到的帖子标题：{}", collectionResult.get("title"));
        log.info("✅收集到的帖子url：{}", collectionResult.get("url"));

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

        // 获取热门帖子id列表（500条）
        List<Integer> hotPostIdList = restClient.get().uri("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty").retrieve().body(List.class);
        if (CollectionUtils.isEmpty(hotPostIdList)) {
            throw new NullPointerException("收集数据节点获取热门帖子id列表失败，返回结果为空");
        }

        // 遍历热门帖子id列表，获取第一个未被采集的帖子详情
        JSONObject jsonObject = null;
        for (Integer postId : hotPostIdList) {
            PostTaskResult postTaskResult = postTaskResultRepository.findByDataId(postId);
            if (postTaskResult == null) {
                log.info("开始获取帖子id= {} 的详细信息🔎...", postId);
                String result = restClient.get().uri("https://hacker-news.firebaseio.com/v0/item/" + postId + ".json?print=pretty").retrieve().body(String.class);
                if (StringUtils.hasText(result)) {
                    jsonObject = JSON.parseObject(result);
                    int score = jsonObject.getIntValue("score");
                    if (score > 60) {
                        break;
                    }
                }
                log.info("帖子id= {} 已被采集，跳过", postId);
            }
        }

        // 解析帖子详情json字符串
        String url = jsonObject.getString("url");
        String title = jsonObject.getString("title");
        String id = jsonObject.getString("id");

        return Map.of("url", url, "title", title, "postId", id);
    }

}
