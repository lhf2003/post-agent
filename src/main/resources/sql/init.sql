create database if not exists post_agent;

create table if not exists post_task
(
    id            bigint auto_increment primary key comment '任务ID',
    task_name     varchar(255) comment '任务名称',
    status        varchar(255) comment '任务状态',
    target_origin varchar(255) comment '数据来源',
    description   varchar(255) comment '任务描述',
    create_time   datetime  default current_timestamp comment '创建时间',
    update_by     timestamp default current_timestamp on update current_timestamp comment '更新时间'
);

create table if not exists post_task_result
(
    id               bigint auto_increment primary key comment '任务结果ID',
    task_id          bigint comment '任务ID',
    status           varchar(255) comment '任务状态',
    output_directory varchar(255) comment '文件输出目录',
    description      varchar(255) comment '任务描述',
    create_time      datetime default current_timestamp
);

create table if not exists user_info
(
    id          bigint auto_increment primary key comment '用户ID',
    username    varchar(50) comment '用户名',
    password    varchar(255) comment '密码',
    email       varchar(50) comment '邮箱',
    phone       varchar(50) comment '手机号',
    avatar      varchar(255) comment '头像',
    create_time datetime  default current_timestamp comment '创建时间',
    update_time timestamp default current_timestamp on update current_timestamp comment '更新时间'
);