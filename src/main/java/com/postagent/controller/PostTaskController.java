package com.postagent.controller;

import com.postagent.entity.PostTask;
import com.postagent.service.PostTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 帖子任务控制器
 */
@RestController
@RequestMapping("/post-task")
public class PostTaskController {

    @Autowired
    private PostTaskService postTaskService;

    /**
     * 添加一个新的任务
     * @param postTask 任务对象
     * @return 添加结果
     */
    @PostMapping("/add")
    public ResponseEntity<?> addPostTask(@RequestBody PostTask postTask) {
        postTask.setStatus(PostTask.Status.PENDING.getValue());
        postTaskService.addPostTask(postTask);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取分页的任务列表
     * @return 任务列表
     */
    @GetMapping("/page")
    public ResponseEntity<?> getPostTasksByPage(Pageable pageable) {
        return ResponseEntity.ok(postTaskService.getPostTasksByPage(pageable));
    }

    /**
     * 执行指定的任务
     * @param taskId 任务ID
     * @return 执行结果
     */
    @GetMapping("/execute")
    public ResponseEntity<?> executePostTask(@RequestParam("id") Long taskId) {
        postTaskService.executePostTask(taskId);
        return ResponseEntity.ok().build();
    }

}