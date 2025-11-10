package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.postagent.entity.PostTask;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
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

    public DataCollectorNode(RestClient.Builder restClient) {
        this.restClient = restClient.build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        log.info("======DataCollectorNode apply start======");

        //TODO post_origin目前是是死值
        PostTask taskObject = (PostTask) state.value("task_object").get();
        String postOrigin = taskObject.getTargetOrigin();

        // 解析收集结果
        Map<String, String> collectionResult = parseCollectionResult(postOrigin);

        log.info("======DataCollectorNode apply end======");

        return Map.of("collectedUrl", collectionResult.get("url"), "collectedTitle", collectionResult.get("title"));
    }

    /**
     * 获取目标参考源的帖子信息
     * @param origin 用户输入的参考源
     * @return url
     */
    public Map<String, String> parseCollectionResult(String origin) throws ResourceAccessException, NullPointerException {
        log.info("开始获取热门帖子🔎...");
        int retryCount = 0;
        String result = "";
        while (retryCount <= 3) {
            try {
                List<Integer> hotPostIdList = restClient.get().uri("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty").retrieve().body(List.class);
                Integer hotId = hotPostIdList.get(0);
                log.info("当前最热门的帖子id：{}", hotId);
                //TODO 基于数据库判断该数据库是否已收集过。如果已收集过，拿第二个id，以此类推。
                Integer hotPostId = hotId;
                log.info("开始获取帖子详细信息🔎...");
                result = restClient.get().uri("https://hacker-news.firebaseio.com/v0/item/" + hotPostId + ".json?print=pretty").retrieve().body(String.class);
                log.info("✅获取到的帖子信息：\n {}", result);
                break;
            } catch (ResourceAccessException e) {
                retryCount++;
                if (retryCount > 3) {
                    log.error("收集数据节点获取数据失败，重试次数已达上限");
                    throw e;
                }
                log.warn("收集数据节点获取数据失败，原因:{}，第{}次重试", e.getMessage(), retryCount);
            }
        }
        if (!StringUtils.hasText(result)) {
            throw new NullPointerException("收集数据节点获取数据失败，返回结果为空");
        }

        JSONObject jsonObject = JSON.parseObject(result);
        String url = jsonObject.getString("url");
        String title = jsonObject.getString("title");

        return Map.of("url", url, "title", title);
    }

//    /**
//     * 获取目标参考源的帖子信息
//     * @param origin 用户输入的参考源
//     * @return url列表
//     */
//    private List<String> parseCollectionResult(String origin) {
//        List<String> urlList = new ArrayList<>();
//
//        List<Long> hotPostIdList = restClient.get().uri("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty").retrieve().body(List.class);
//        if (CollectionUtils.isEmpty(hotPostIdList)) {
//            log.warn("获取到的热门帖子ID列表为空");
//            return urlList;
//        }
//
//        // TODO 10现在是固定值，后续可以根据需要调整
//        for (int i = 0; i < 10; i++) {
//            Long hotPostId = hotPostIdList.get(i);
//            String postUrl = restClient.get().uri("https://hacker-news.firebaseio.com/v0/item/" + hotPostId + ".json?print=pretty").retrieve().body(String.class);
//            urlList.add(postUrl);
//        }
//        return urlList;
//    }

}
