package com.postagent.repository;

import com.postagent.entity.PostTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostTaskRepository extends JpaRepository<PostTask, Long> {

    /**
     * 根据分页信息查询任务列表
     * @param pageable 分页信息
     * @return 任务列表
     */
    @Query("SELECT p FROM PostTask p ORDER BY p.createTime DESC")
    List<PostTask> findByPage(Pageable pageable);
}