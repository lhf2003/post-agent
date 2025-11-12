package com.postagent.graph;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.postagent.dispatcher.ExceptionDispatcher;
import com.postagent.nodes.DataCollectorNode;
import com.postagent.nodes.DownloadNode;
import com.postagent.nodes.SummarizeNode;
import com.postagent.nodes.TransformNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.GraphRepresentation.Type.MERMAID;
import static com.alibaba.cloud.ai.graph.OverAllState.DEFAULT_INPUT_KEY;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 小红书笔记工作流配置
 */
@Slf4j
@Configuration
public class GraphConfig {
    @Resource
    private DataCollectorNode dataCollectorNode;

    @Resource
    private DownloadNode downloadNode;

    @Resource
    private SummarizeNode summarizeNode;

    @Resource
    private TransformNode transformNode;

    @Bean
    public KeyStrategyFactory keyStrategyFactory() {
        return new KeyStrategyFactoryBuilder().addStrategy(DEFAULT_INPUT_KEY, KeyStrategy.REPLACE)
                .addStrategy("task_id", KeyStrategy.REPLACE) // 任务id
                .addStrategy("target_origin", KeyStrategy.REPLACE) // 任务参考源
                .addStrategy("status", KeyStrategy.REPLACE) // 任务执行状态
                .addStrategy("output_directory", KeyStrategy.REPLACE) // 任务结果保存目录
                .build();
    }

    @Bean
    public StateGraph postAgentGraph(KeyStrategyFactory keyStrategyFactory) throws GraphStateException {
        log.info("构建小红书笔记工作流...");

        StateGraph graph = new StateGraph("XiaoHongShu Workflow", keyStrategyFactory)
                // 添加节点
                .addNode("collector_agent", node_async(dataCollectorNode))
                .addNode("download_agent", node_async(downloadNode))
                .addNode("summarize_agent", node_async(summarizeNode))
                .addNode("transform_agent", node_async(transformNode))
                // 定义边
                .addEdge(START, "collector_agent") // 开始节点
                .addEdge("collector_agent", "download_agent")
                .addConditionalEdges("download_agent",
                        AsyncEdgeAction.edge_async(new ExceptionDispatcher()),
                        Map.of("summarize_agent", "summarize_agent", END, END))
                .addEdge("summarize_agent", "transform_agent")
                .addEdge("transform_agent", END); // 结束节点

        log.info("小红书笔记工作流构建完成");
        return graph;
    }

    @Bean(name = "compiledPostAgentGraph")
    public CompiledGraph compiledPostAgentGraph(@Qualifier("postAgentGraph") StateGraph stateGraph)
            throws GraphStateException {
        log.info("小红书笔记工作流开始编译...");

        CompiledGraph compiledGraph = stateGraph.compile(CompileConfig.builder().build());
        System.out.println(compiledGraph.getGraph(MERMAID));
        // 设置最大迭代次数
        compiledGraph.setMaxIterations(100);
        // 配置定时任务，每15分钟执行一次
//        compiledGraph.schedule(ScheduleConfig.builder().cronExpression("0 0/5 * * * ?").build());

        log.info("小红书笔记工作流编译完成");
        return compiledGraph;
    }

}
