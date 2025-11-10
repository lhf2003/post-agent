package com.postagent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.postagent.service.PythonScriptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 下载节点 将指定url中的文本内容提取为markdown格式并下载到本地
 */
@Slf4j
@Component
public class DownloadNode implements NodeAction {

    @Resource
    private PythonScriptService pythonScriptService;

    // 脚本输出路径
    private static final String DOWNLOAD_DIR_NAME = System.getProperty("user.dir") + File.separator + "out";

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("======DownloadNode apply start======");

        String collectedUrl = state.value("collectedUrl").get().toString();
        if (!StringUtils.hasText(collectedUrl)) {
            log.info("DownloadNode apply urlList is empty");
            // TODO 错误处理
            return Map.of();
        }

        // 格式化当前时间为字符串作为文件名
        LocalDateTime now = LocalDateTime.now();
        String downloadDirName = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // 写入数据的文件目录
        String targetDir = DOWNLOAD_DIR_NAME + File.separator + downloadDirName;
        boolean mkdirsed = new File(targetDir).mkdirs();
        if (!mkdirsed) {
            throw new IOException("Failed to create directory: " + targetDir);
        }

        // 执行python脚本
        String result = pythonScriptService.executeScript("downloadToMarkdown.py", collectedUrl, List.of("-o", targetDir));
        // TODO 错误处理
        if (result.contains("Error")) {
            throw new IOException("Python script execution failed: " + result);
        }
        // 向指定文件写入脚本响应（日志）
        FileWriter writer = new FileWriter(targetDir + File.separator + "result.log", StandardCharsets.UTF_8, true);
        writer.write(result);
        writer.close();
        log.info("\uD83D\uDCD6下载成功");
        log.info("✅下载的.md文件存储路径：{}", targetDir);
        log.info("======DownloadNode apply end======");
        return Map.of("targetDir", targetDir);
    }

}