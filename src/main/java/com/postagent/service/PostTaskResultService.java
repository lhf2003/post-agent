package com.postagent.service;

import com.postagent.entity.PostTaskResult;
import com.postagent.repository.PostTaskResultRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class PostTaskResultService {
    @Resource
    private PostTaskResultRepository postTaskResultRepository;

    public PostTaskResult findByDataId(long hotId) {
        return postTaskResultRepository.findByDataId(hotId);
    }
}