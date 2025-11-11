package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.postagent.service.PythonScriptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 转换节点 将总结好的文案转换为图片
 */
@Slf4j
@Component
public class TransformNode implements NodeAction {

    @Resource
    private PythonScriptService pythonScriptService;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("======transformNode apply start======");
        String summaryContent = state.value("summary_content").get().toString();
        String collectedTitle = state.value("collectedTitle").get().toString();
        String targetDir = state.value("targetDir").get().toString();

        JSONObject aiResult = JSON.parseObject(summaryContent);
        generateCoverImage(aiResult, collectedTitle, targetDir);
        generateContentImage(aiResult, collectedTitle, targetDir);

        log.info("✅图片存储路径：{}", targetDir);
        return Map.of();
    }

    /**
     * 生成封面图片
     * @param aiResult 包含标题和emoji的json对象
     * @param collectedTitle 收集到的标题
     * @param targetDir 目标目录
     * @throws IOException 生成图片时出现异常
     */
    private void generateCoverImage(JSONObject aiResult, String collectedTitle, String targetDir) throws IOException {
        // 拼接命令参数列表
        List<String> argList = new ArrayList<>();
        // 封面标题
        argList.add("--title");
        argList.add(aiResult.getJSONArray("title").getString(0));
        // emoji
//        argList.add("----decor-emoji");
//        argList.add("\uD83E\uDD29");
        // 图片名称
        argList.add("--name");
        argList.add(collectedTitle + "_cover");
        // 输出目录
        argList.add("--out");
        argList.add(targetDir);

        pythonScriptService.executeScript("textTransformToPng.py", targetDir, "", argList);
    }

    /**
     * 生成内容图片
     * @param aiResult 包含标题和emoji的json对象
     * @param collectedTitle 收集到的标题
     * @param targetDir 目标目录
     * @throws IOException 生成图片时出现异常
     */
    private void generateContentImage(JSONObject aiResult, String collectedTitle, String targetDir) throws IOException {
        String content = aiResult.getString("summary");
        // 拼接命令参数列表
        List<String> argList = new ArrayList<>();
        // 图片内容
        argList.add("--content");
        argList.add(content);
        // 图片名称
        argList.add("--name");
        argList.add(collectedTitle + "_content");
        // 输出目录
        argList.add("--out");
        argList.add(targetDir);

        pythonScriptService.executeScript("content_transform.py", targetDir, "", argList);
    }
}