package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.postagent.entity.PostTask;
import com.postagent.service.PythonScriptService;
import jakarta.annotation.Resource;
import jakarta.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
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
        argList.add(collectedTitle);
        // 输出目录
        argList.add("--out");
        argList.add(targetDir);

        String result = pythonScriptService.executeScript("textTransformToPng.py", "", argList);

        // 向指定文件写入脚本响应（日志）
        FileWriter writer = new FileWriter(targetDir + File.separator + "result.log", StandardCharsets.UTF_8, true);
        writer.write(result);
        writer.close();

        log.info("✅图片存储路径：{}", targetDir);
        log.info("======transformNode apply end======");
        return Map.of();
    }
}