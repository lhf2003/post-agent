package com.postagent.service;

import com.postagent.config.PythonProperties;
import com.postagent.exception.PromptProcessingException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service

public class PythonScriptService {

    @Resource
    private PythonProperties props;

    /**
     * 执行python脚本
     * @param content 要处理的内容
     * @param args 执行参数
     * @return 执行进程
     * @throws Exception 执行脚本时出现异常
     */
    public void executeScript(String scriptName, String targetDir, String content, List<String> args) throws IOException {

        Path script = props.getScriptDir().resolve(scriptName);
        if (!Files.exists(script)) {
            throw new PromptProcessingException("Script not found: " + script);
        }

        List<String> cmd = new ArrayList<>();
        // python解释器路径
        cmd.add(props.getInterpreter().toString());
        // 脚本路径
        cmd.add(script.toString());
        // 要处理的内容,比如从指定url下载文件
        if (StringUtils.hasText(content)) {
            cmd.add(content);
        }
        // 执行参数
        if (!CollectionUtils.isEmpty(args)) {
            cmd.addAll(args);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(props.getScriptDir().toFile())
                .redirectErrorStream(true);

        log.info("Running python script: {}", scriptName);
        Process process = pb.start();
        String outputData = readProcessOutput(process.getInputStream());

        // 向指定文件写入脚本响应（日志）
        FileWriter writer = new FileWriter(targetDir + File.separator + "result.log", StandardCharsets.UTF_8, true);
        writer.write(outputData);
        writer.close();

        // TODO 错误处理
        if (outputData.contains("Error")) {
            throw new IOException("Python script execution failed: " + outputData);
        }
    }

    /**
     * 读取进程输出
     */
    private String readProcessOutput(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}