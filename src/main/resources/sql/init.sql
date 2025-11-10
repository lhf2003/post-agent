create database if not exists post_agent;

create table if not exists post_task (
   id bigint auto_increment primary key,
   task_name varchar(255),
   status varchar(255),
   target_origin varchar(255),
   description varchar(255),
   create_time datetime default current_timestamp,
   update_by timestamp default current_timestamp on update current_timestamp
);

create table if not exists post_task_result (
    id bigint auto_increment primary key comment '任务结果ID',
    task_id bigint comment '任务ID',
    status varchar(255) comment '任务状态',
    output_directory varchar(255) comment '文件输出目录',
    description varchar(255) comment '任务描述',
    create_time datetime default current_timestamp
);
