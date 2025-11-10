package com.postagent.repository;

import com.postagent.entity.PostTaskResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostTaskResultRepository extends JpaRepository<PostTaskResult, Long> {
    @Query("select p from PostTaskResult p where p.dataId = ?1")
    PostTaskResult findByDataId(long dataId);
}