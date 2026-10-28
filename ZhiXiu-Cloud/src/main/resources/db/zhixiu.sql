-- ============================================================================
--  物业报修系统（ZhiXiu）· 数据库初始化脚本
-- ----------------------------------------------------------------------------
--  数据库      : zhixiu
--  数据库版本  : MySQL 8.0
--  字符集/引擎 : utf8mb4 / InnoDB
--  表数量      : 15 张（业务 12 张 + AI 3 张）
--  设计约定    : 表间均为逻辑外键，不建物理外键约束
--                主键统一 BIGINT（雪花 ID，MyBatis-Plus ASSIGN_ID）
--                通用字段 create_time / update_time / deleted（0 正常 1 删除）
--  依据文档    : 物业报修系统-开发文档.md（数据库设计章节）· 数据库ER关系图.html
--  执行方式    : mysql -u root -p < zhixiu.sql（或直接粘贴进 Navicat 执行）
-- ============================================================================

-- 建库：utf8mb4 可完整保存中文与 emoji；utf8mb4_general_ci 为不区分大小写的通用排序规则
CREATE DATABASE IF NOT EXISTS `zhixiu`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `zhixiu`;

-- 客户端连接字符集，避免脚本中的中文注释/初始数据乱码
SET NAMES utf8mb4;

-- ============================================================================
--  可重复执行：先清理旧表（表间无物理外键，删除顺序仅便于阅读）
--  临时关闭外键检查，保证脚本可反复执行且不受删除顺序影响
-- ============================================================================
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `ai_chat_message`;
DROP TABLE IF EXISTS `ai_chat_session`;
DROP TABLE IF EXISTS `repair_knowledge`;
DROP TABLE IF EXISTS `work_order_log`;
DROP TABLE IF EXISTS `evaluation`;
DROP TABLE IF EXISTS `work_order`;
DROP TABLE IF EXISTS `maintenance_worker`;
DROP TABLE IF EXISTS `property`;
DROP TABLE IF EXISTS `repair_category`;
DROP TABLE IF EXISTS `sla_config`;
DROP TABLE IF EXISTS `announcement`;
DROP TABLE IF EXISTS `feedback`;
DROP TABLE IF EXISTS `operation_log`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `role`;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
--  一、业务表（12 张）
-- ============================================================================

-- 2.1 role 角色表 ------------------------------------------------------------
-- 设计意图：RBAC 的角色字典表。role_code 是程序内稳定标识（OWNER/WORKER/ADMIN），
-- role_name 仅用于界面展示；uk_role_code 保证编码唯一，user.role_id 逻辑关联本表。
CREATE TABLE `role` (
  `id`          BIGINT       NOT NULL COMMENT '主键',
  `role_code`   VARCHAR(20)  NOT NULL COMMENT '角色编码：OWNER/WORKER/ADMIN',
  `role_name`   VARCHAR(50)  NOT NULL COMMENT '角色名称',
  `description` VARCHAR(255)          DEFAULT NULL COMMENT '描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 2.2 user 用户表 ------------------------------------------------------------
-- 设计意图：业主/师傅/管理员共用的统一账号表；role_id 指向 role.id（逻辑外键）。
-- uk_username/uk_phone 防止重复注册，idx_role_id/idx_status 支撑按角色和状态筛选。
-- password 保存 BCrypt 密文；deleted=1 为逻辑删除，登录与查询均需过滤 deleted=0。
CREATE TABLE `user` (
  `id`              BIGINT       NOT NULL COMMENT '主键',
  `username`        VARCHAR(50)  NOT NULL COMMENT '登录名',
  `password`        VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
  `real_name`       VARCHAR(50)           DEFAULT NULL COMMENT '真实姓名',
  `phone`           VARCHAR(20)  NOT NULL COMMENT '手机号',
  `email`           VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
  `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像 URL',
  `gender`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0未知 1男 2女',
  `role_id`         BIGINT       NOT NULL COMMENT '角色ID',
  `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  `last_login_time` DATETIME              DEFAULT NULL COMMENT '最近登录时间',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_role_id` (`role_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2.3 property 房产表 --------------------------------------------------------
-- 设计意图：一个业主可登记多套房产，owner_id 逻辑关联 user.id；
-- uk_address 保证“小区+栋+单元+房号”唯一，is_default 标记报修时默认选中的地址。
CREATE TABLE `property` (
  `id`           BIGINT       NOT NULL,
  `owner_id`     BIGINT       NOT NULL COMMENT '业主 user_id',
  `community`    VARCHAR(100) NOT NULL COMMENT '小区',
  `building`     VARCHAR(20)  NOT NULL COMMENT '栋',
  `unit`         VARCHAR(20)  NOT NULL COMMENT '单元',
  `room`         VARCHAR(20)  NOT NULL COMMENT '房号',
  `full_address` VARCHAR(255) NOT NULL COMMENT '完整地址',
  `area_size`    DECIMAL(10,2)         DEFAULT NULL COMMENT '面积(㎡)',
  `is_default`   TINYINT      NOT NULL DEFAULT 0 COMMENT '0否 1默认',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_owner_id` (`owner_id`),
  UNIQUE KEY `uk_address` (`community`,`building`,`unit`,`room`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房产表';

-- 2.4 repair_category 报修类型表（树形） --------------------------------------
-- 设计意图：两级树形字典，parent_id=0 为一级分类，二级分类的 parent_id 指向一级 ID；
-- idx_parent_id 支撑按父节点加载子分类；工单只允许选择二级分类。
CREATE TABLE `repair_category` (
  `id`          BIGINT       NOT NULL,
  `name`        VARCHAR(50)  NOT NULL COMMENT '类型名称',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父类型ID，0为一级',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `icon`        VARCHAR(255)          DEFAULT NULL COMMENT '图标 URL',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报修类型表';

-- 2.5 maintenance_worker 维修师傅扩展表 ---------------------------------------
-- 设计意图：师傅扩展信息与 user 一对一（uk_user_id），工号 uk_worker_no 唯一。
-- work_status 表示接单状态，audit_status 表示入驻审核状态，二者索引用于派单筛选和审核列表。
-- total_orders/finish_orders/avg_score/current_load 为冗余统计字段，评价与状态流转时回写。
CREATE TABLE `maintenance_worker` (
  `id`            BIGINT       NOT NULL,
  `user_id`       BIGINT       NOT NULL COMMENT '关联 user.id',
  `worker_no`     VARCHAR(50)  NOT NULL COMMENT '工号',
  `skill_tags`    VARCHAR(255)          DEFAULT NULL COMMENT '技能标签，逗号分隔',
  `id_card`       VARCHAR(20)           DEFAULT NULL COMMENT '身份证号',
  `work_status`   TINYINT      NOT NULL DEFAULT 0 COMMENT '0审核中 1在线 2离线 3忙碌 4禁用',
  `audit_status`  TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  `audit_remark`  VARCHAR(255)          DEFAULT NULL COMMENT '审核备注',
  `total_orders`  INT          NOT NULL DEFAULT 0 COMMENT '累计接单量',
  `finish_orders` INT          NOT NULL DEFAULT 0 COMMENT '累计完成量',
  `avg_score`     DECIMAL(3,1) NOT NULL DEFAULT 0.0 COMMENT '平均评分',
  `current_load`  INT          NOT NULL DEFAULT 0 COMMENT '当前进行中工单数',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_worker_no` (`worker_no`),
  KEY `idx_audit_status` (`audit_status`),
  KEY `idx_work_status` (`work_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修师傅扩展表';

-- 2.6 work_order 工单表（核心） ----------------------------------------------
-- 设计意图：系统核心业务表，保存工单从创建到关闭的全生命周期。
-- status 取值见字段注释；source 区分手填与 AI 助手生成，ai_draft_id 关联 AI 草稿消息。
-- assigned_worker_id 记录派给谁，accepted_worker_id 记录实际接单人（抢单/转单后可能不同）。
-- assigned/accepted/submitted/completed 四个时间点用于计算响应、处理时长与 SLA 判定。
-- SLA 字段保存响应/处理超时点及超时、升级标记，由延迟消息异步更新。
-- version 为乐观锁版本号，防止并发状态流转相互覆盖。
-- 索引设计：owner/property/worker 支撑个人工单列表；status/urgency 支撑筛选与派单；
-- create_time 支撑按时间统计；sla_escalated 支撑升级任务扫描。
CREATE TABLE `work_order` (
  `id`                  BIGINT       NOT NULL,
  `order_no`            VARCHAR(32)  NOT NULL COMMENT '工单号',
  `property_id`         BIGINT       NOT NULL COMMENT '报修房产ID',
  `owner_id`            BIGINT       NOT NULL COMMENT '业主 user_id',
  `category_id`         BIGINT       NOT NULL COMMENT '报修类型ID（二级）',
  `title`               VARCHAR(100) NOT NULL COMMENT '工单标题',
  `description`         TEXT                  COMMENT '故障现象描述',
  `fault_duration`      VARCHAR(50)           DEFAULT NULL COMMENT '故障持续时间，如"约1天"',
  `affect_scope`        TINYINT      NOT NULL DEFAULT 1 COMMENT '影响范围：1本户 2本层 3整栋',
  `safety_flag`         VARCHAR(50)           DEFAULT NULL COMMENT '安全隐患标志：涉电/涉水/涉气/高空，逗号分隔',
  `device_info`         VARCHAR(100)          DEFAULT NULL COMMENT '设备品牌型号',
  `expected_visit_time` VARCHAR(50)           DEFAULT NULL COMMENT '期望上门时段，如"今天下午"',
  `contact_phone`       VARCHAR(20)           DEFAULT NULL COMMENT '联系人电话',
  `urgency`             TINYINT      NOT NULL DEFAULT 2 COMMENT '紧急程度：1低 2中 3高 4紧急',
  `images`              VARCHAR(1000)         DEFAULT NULL COMMENT '图片URL，逗号分隔',
  `status`              VARCHAR(20)  NOT NULL COMMENT '状态：PENDING/ASSIGNED/ACCEPTED/GOING/PAUSED/TRANSFER_PENDING/SUBMITTED/COMPLETED/CLOSED/CANCELED',
  `source`              VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL手填 AI智能助手',

  `assigned_worker_id`  BIGINT                DEFAULT NULL COMMENT '派单给谁(worker.id)',
  `accepted_worker_id`  BIGINT                DEFAULT NULL COMMENT '实际接单(worker.id)',
  `assigned_time`       DATETIME              DEFAULT NULL COMMENT '派单时间',
  `accepted_time`       DATETIME              DEFAULT NULL COMMENT '接单时间',
  `arrived_time`        DATETIME              DEFAULT NULL COMMENT '到场时间',
  `submitted_time`      DATETIME              DEFAULT NULL COMMENT '完工提交时间',
  `completed_time`      DATETIME              DEFAULT NULL COMMENT '完工确认时间',
  `closed_time`         DATETIME              DEFAULT NULL COMMENT '关闭时间',
  `cancel_reason`       VARCHAR(255)          DEFAULT NULL COMMENT '取消原因',
  `transfer_reason`     VARCHAR(255)          DEFAULT NULL COMMENT '转单原因',
  `process_note`        TEXT                  COMMENT '处理过程记录',
  `finish_images`       VARCHAR(1000)         DEFAULT NULL COMMENT '完工照片URL，逗号分隔',

  `sla_response_deadline` DATETIME            DEFAULT NULL COMMENT '响应超时点',
  `sla_handle_deadline`   DATETIME            DEFAULT NULL COMMENT '处理超时点',
  `sla_escalated`         TINYINT      NOT NULL DEFAULT 0 COMMENT '0未升级 1已升级',
  `sla_response_breached` TINYINT      NOT NULL DEFAULT 0 COMMENT '响应是否超时',
  `sla_handle_breached`   TINYINT      NOT NULL DEFAULT 0 COMMENT '处理是否超时',

  `ai_draft_id`         BIGINT                DEFAULT NULL COMMENT '来源 AI 草稿ID(ai_chat_message.id)',
  `version`             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`             TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_owner_id` (`owner_id`),
  KEY `idx_property_id` (`property_id`),
  KEY `idx_accepted_worker_id` (`accepted_worker_id`),
  KEY `idx_status` (`status`),
  KEY `idx_urgency` (`urgency`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_sla_escalated` (`sla_escalated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

-- 2.7 work_order_log 工单流转日志表 -------------------------------------------
-- 设计意图：状态机审计表，每次工单动作写一条，记录“谁在何时把工单从什么状态改到什么状态”。
-- operator_type 区分操作者类型，action 与状态枚举配合描述动作；
-- idx_order_id/create_time 支撑工单详情页的时间线查询。
CREATE TABLE `work_order_log` (
  `id`            BIGINT       NOT NULL,
  `order_id`      BIGINT       NOT NULL COMMENT '工单ID',
  `operator_id`   BIGINT                DEFAULT NULL COMMENT '操作人ID',
  `operator_type` VARCHAR(20)           DEFAULT NULL COMMENT 'OWNER/WORKER/ADMIN/SYSTEM',
  `action`        VARCHAR(50)  NOT NULL COMMENT 'CREATE/ASSIGN/ACCEPT/ARRIVE/PAUSE/RESUME/TRANSFER/SYSTEM_SUBMIT/COMPLETE/CANCEL',
  `from_status`   VARCHAR(20)           DEFAULT NULL COMMENT '原状态',
  `to_status`     VARCHAR(20)           DEFAULT NULL COMMENT '新状态',
  `remark`        VARCHAR(500)          DEFAULT NULL COMMENT '备注',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单流转日志表';

-- 2.8 evaluation 评价表 ------------------------------------------------------
-- 设计意图：业主对完工工单的评价，uk_order_id 保证一单只能评价一次。
-- score 为总分 1-5，speed/attitude/quality 为可选分项；idx_worker_id 支撑师傅评分统计与榜单。
CREATE TABLE `evaluation` (
  `id`             BIGINT      NOT NULL,
  `order_id`       BIGINT      NOT NULL COMMENT '工单ID',
  `owner_id`       BIGINT      NOT NULL COMMENT '业主ID',
  `worker_id`      BIGINT      NOT NULL COMMENT '师傅ID(worker.id)',
  `score`          TINYINT     NOT NULL COMMENT '总分 1-5',
  `speed_score`    TINYINT              DEFAULT NULL COMMENT '响应速度分',
  `attitude_score` TINYINT              DEFAULT NULL COMMENT '服务态度分',
  `quality_score`  TINYINT              DEFAULT NULL COMMENT '维修质量分',
  `content`        TEXT                 COMMENT '评价内容',
  `images`         VARCHAR(1000)        DEFAULT NULL COMMENT '评价图片',
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_worker_id` (`worker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';

-- 2.9 sla_config SLA 配置表 --------------------------------------------------
-- 设计意图：SLA 规则表，按报修类型/紧急程度/安全隐患/影响范围匹配工单；
-- 各条件字段为 NULL 表示该维度通配（不限制），命中后使用对应 response_timeout/handle_timeout。
-- 超时时间单位为分钟；priority_weight 越大派单优先级越高；escalate_to 指定升级通知角色。
-- idx_category_urgency 支撑按类型与紧急程度快速匹配规则。
CREATE TABLE `sla_config` (
  `id`               BIGINT      NOT NULL,
  `category_id`      BIGINT               DEFAULT NULL COMMENT '报修类型ID，NULL=全局',
  `urgency`          TINYINT              DEFAULT NULL COMMENT '紧急程度，NULL=所有',
  `safety_flag`      VARCHAR(50)          DEFAULT NULL COMMENT '安全隐患标志，NULL=所有',
  `affect_scope`     TINYINT              DEFAULT NULL COMMENT '影响范围，NULL=所有',
  `response_timeout` INT         NOT NULL COMMENT '响应超时(分钟)',
  `handle_timeout`   INT         NOT NULL COMMENT '处理超时(分钟)',
  `escalate_to`      VARCHAR(50)          DEFAULT NULL COMMENT '升级通知角色：ADMIN',
  `priority_weight`  INT         NOT NULL DEFAULT 0 COMMENT '派单优先级权重',
  `enabled`          TINYINT     NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_category_urgency` (`category_id`,`urgency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA配置表';

-- 2.10 announcement 公告表 ---------------------------------------------------
-- 设计意图：物业公告，type 区分系统/维护/活动，target_role 控制可见角色；
-- status 区分草稿与已发布，is_top 控制置顶，view_count 统计阅读量；
-- idx_status/idx_publish_time 支撑公告列表按发布状态与时间排序。
CREATE TABLE `announcement` (
  `id`           BIGINT       NOT NULL,
  `title`        VARCHAR(200) NOT NULL,
  `content`      TEXT         NOT NULL,
  `type`         VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM/MAINTENANCE/ACTIVITY',
  `target_role`  VARCHAR(20)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL/OWNER/WORKER',
  `publisher_id` BIGINT       NOT NULL,
  `publish_time` DATETIME              DEFAULT NULL COMMENT '发布时间',
  `is_top`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0否 1置顶',
  `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布',
  `view_count`   INT          NOT NULL DEFAULT 0,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_publish_time` (`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告表';

-- 2.11 feedback 问题反馈表 ---------------------------------------------------
-- 设计意图：用户问题反馈，type 区分 BUG/建议/投诉；status 表示处理流程状态；
-- reply/replier_id/reply_time 保存管理员回复；idx_user_id/status 支撑“我的反馈”与待处理列表。
CREATE TABLE `feedback` (
  `id`          BIGINT       NOT NULL,
  `user_id`     BIGINT       NOT NULL COMMENT '提交人',
  `user_role`   VARCHAR(20)  NOT NULL COMMENT '提交人角色',
  `type`        VARCHAR(20)  NOT NULL DEFAULT 'SUGGESTION' COMMENT 'BUG/SUGGESTION/COMPLAINT',
  `title`       VARCHAR(200) NOT NULL,
  `content`     TEXT         NOT NULL,
  `contact`     VARCHAR(100)          DEFAULT NULL,
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2已处理 3关闭',
  `reply`       TEXT                  COMMENT '管理员回复',
  `replier_id`  BIGINT                DEFAULT NULL,
  `reply_time`  DATETIME              DEFAULT NULL,
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问题反馈表';

-- 2.12 operation_log 操作日志表 ----------------------------------------------
-- 设计意图：接口操作审计日志，由 @ApiLog 切面写入；
-- module/action/method/url/params 记录请求上下文，result/error_msg/cost_time 记录执行结果与耗时；
-- idx_user_id/module/create_time 支撑按用户、模块和时间检索。
CREATE TABLE `operation_log` (
  `id`          BIGINT       NOT NULL,
  `user_id`     BIGINT                DEFAULT NULL,
  `username`    VARCHAR(50)           DEFAULT NULL,
  `role`        VARCHAR(20)           DEFAULT NULL,
  `module`      VARCHAR(50)           DEFAULT NULL COMMENT '模块',
  `action`      VARCHAR(50)           DEFAULT NULL COMMENT '操作',
  `method`      VARCHAR(10)           DEFAULT NULL COMMENT 'HTTP方法',
  `url`         VARCHAR(255)          DEFAULT NULL,
  `params`      TEXT                  COMMENT '请求参数',
  `ip`          VARCHAR(50)           DEFAULT NULL,
  `result`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0失败 1成功',
  `error_msg`   TEXT                  COMMENT '错误信息',
  `cost_time`   INT                   DEFAULT NULL COMMENT '耗时(ms)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================================================
--  二、AI 表（3 张）
-- ============================================================================

-- 2.13 ai_chat_session AI 会话表 ----------------------------------------------
-- 设计意图：AI 会话主表，一次对话一条记录，title 取首条消息摘要；
-- resolved 记录会话结局（0 进行中 / 1 自助解决 / 2 转工单），用于 AI 效果统计。
CREATE TABLE `ai_chat_session` (
  `id`            BIGINT      NOT NULL,
  `user_id`       BIGINT      NOT NULL COMMENT '业主 user_id',
  `title`         VARCHAR(100)         DEFAULT NULL COMMENT '会话标题（取首条消息摘要）',
  `message_count` INT         NOT NULL DEFAULT 0 COMMENT '消息数',
  `resolved`      TINYINT     NOT NULL DEFAULT 0 COMMENT '0进行中 1自助解决 2转工单',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话表';

-- 2.14 ai_chat_message AI 消息表 ----------------------------------------------
-- 设计意图：AI 会话消息明细，sender 区分 USER/AI；
-- intent 记录识别意图，citations 保存知识库引用，form_draft/filled_slots/missing_slots 支撑工单槽位收集；
-- prompt_tokens/completion_tokens/cost_ms 用于调用成本与性能统计。
CREATE TABLE `ai_chat_message` (
  `id`                BIGINT      NOT NULL,
  `session_id`        BIGINT      NOT NULL COMMENT '会话ID',
  `sender`            VARCHAR(10) NOT NULL COMMENT 'USER / AI',
  `content`           TEXT                 COMMENT '消息内容(Markdown)',
  `intent`            VARCHAR(20)          DEFAULT NULL COMMENT 'QA问答 / TROUBLESHOOT排查 / FILL_FORM收集信息',
  `citations`         VARCHAR(500)         DEFAULT NULL COMMENT '引用的知识库ID',
  `form_draft`        JSON                 COMMENT '工单草稿(JSON)，仅收集信息阶段',
  `filled_slots`      VARCHAR(500)         DEFAULT NULL COMMENT '已填充槽位名，逗号分隔',
  `missing_slots`     VARCHAR(500)         DEFAULT NULL COMMENT '缺失槽位名，逗号分隔',
  `prompt_tokens`     INT                  DEFAULT NULL,
  `completion_tokens` INT                  DEFAULT NULL,
  `cost_ms`           INT                  DEFAULT NULL COMMENT '耗时(ms)',
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI消息表';

-- 2.15 repair_knowledge 维修知识库表 ------------------------------------------
-- 设计意图：维修知识源文档，content 会被分块并向量化后写入 RedisVectorStore；
-- vectorized 标记是否已完成向量化，version 在内容修改后递增以触发重新向量化；
-- 向量数据保存在 Redis 中，本表只保存源文档与处理状态。
CREATE TABLE `repair_knowledge` (
  `id`          BIGINT       NOT NULL,
  `title`       VARCHAR(200) NOT NULL COMMENT '标题',
  `category_id` BIGINT                DEFAULT NULL COMMENT '关联报修类型',
  `content`     TEXT         NOT NULL COMMENT '正文（会被分块向量化）',
  `tags`        VARCHAR(255)          DEFAULT NULL COMMENT '标签，逗号分隔',
  `source_type` VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/IMPORT',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `vectorized`  TINYINT      NOT NULL DEFAULT 0 COMMENT '0未向量化 1已向量化',
  `version`     INT          NOT NULL DEFAULT 0 COMMENT '版本（修改后重新向量化）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修知识库表';

-- ============================================================================
--  三、初始化数据
-- ============================================================================

-- 3.1 角色（ID 固定为 1/2/3，业务代码依赖，勿改）
-- ID 被 user.role_id 引用，role_code 为鉴权时使用的稳定编码，三项缺一不可
INSERT INTO `role` (`id`,`role_code`,`role_name`) VALUES
 (1,'OWNER','业主'),(2,'WORKER','维修师傅'),(3,'ADMIN','物业管理员');

-- 3.2 报修类型（一级 parent_id=0，二级指向一级 ID）
-- 100/200/300/400 为一级分类，二级分类覆盖各类型的常见维修物品；
-- 每个一级分类末尾保留一个「其他」选项，用于选择列表未覆盖的物品；
-- 工单必须选择二级分类，因此新增二级分类时 parent_id 需指向已存在的一级 ID
INSERT INTO `repair_category` (`id`,`name`,`parent_id`,`sort`) VALUES
 (100,'水管维修',0,1),(101,'水龙头',100,1),(102,'马桶',100,2),(103,'下水道',100,3),
 (104,'洗手盆',100,4),(105,'淋浴花洒',100,5),(106,'水管漏水',100,6),(107,'其他水管问题',100,7),
 (200,'电器维修',0,2),(201,'空调',200,1),(202,'热水器',200,2),(203,'照明',200,3),
 (204,'洗衣机',200,4),(205,'冰箱',200,5),(206,'电视',200,6),(207,'其他电器问题',200,7),
 (300,'门窗维修',0,3),(301,'入户门',300,1),(302,'窗户',300,2),
 (303,'门锁',300,3),(304,'纱窗',300,4),(305,'其他门窗问题',300,5),
 (400,'公共设施',0,4),(401,'电梯',400,1),(402,'楼道照明',400,2),(403,'单元门禁',400,3),
 (404,'其他公共设施',400,4);

-- 3.3 SLA 配置
-- 规则匹配维度：category_id / urgency / safety_flag / affect_scope，NULL 表示该维度不限；
-- 多条规则命中时优先采用 priority_weight 更高的规则
-- 全局兜底：所有工单 120 分钟内响应、1440 分钟内处理
INSERT INTO `sla_config` (`id`,`category_id`,`urgency`,`safety_flag`,`response_timeout`,`handle_timeout`,`escalate_to`,`priority_weight`)
VALUES (1,NULL,NULL,NULL,120,1440,'ADMIN',0);
-- 涉电强制加急（AI 提取 safety_flag 参与规则匹配的示例）
INSERT INTO `sla_config` (`id`,`category_id`,`urgency`,`safety_flag`,`response_timeout`,`handle_timeout`,`escalate_to`,`priority_weight`)
VALUES (2,NULL,NULL,'涉电',15,120,'ADMIN',100);

-- ============================================================================
--  建库完成：15 张表 + 角色/报修类型/SLA 初始化数据
--  说明：业主、师傅均为自助注册；管理员账号需另行初始化（密码为 BCrypt 密文）
--        应用侧库名由 application.yml 的 ${DB_NAME:zhixiu} 决定，与本脚本保持一致
-- ============================================================================
