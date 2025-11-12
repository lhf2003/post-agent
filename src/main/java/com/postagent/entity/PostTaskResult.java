package com.postagent.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "post_task_result", schema = "post_agent")
public class PostTaskResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @NotNull
    @Column(name = "data_id", nullable = false)
    private Long dataId;

    @Column(name = "status")
    private String status;

    @Column(name = "output_directory")
    private String outputDirectory;

    @Column(name = "description")
    private String description;

    @Column(name = "create_time")
    private Date createTime;

}