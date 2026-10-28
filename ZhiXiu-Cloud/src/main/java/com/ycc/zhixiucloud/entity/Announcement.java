package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公告表 announcement。
 * <p>
 * 保存系统公告、维护通知和社区活动信息，支持按角色定向发布、置顶与浏览量统计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("announcement")
public class Announcement extends BaseEntity {

    /** 公告标题 */
    private String title;

    /** 公告正文内容 */
    private String content;

    /** 公告类型：SYSTEM=系统 / MAINTENANCE=维护 / ACTIVITY=活动 */
    private String type;

    /** 目标角色：ALL=全部 / OWNER=业主 / WORKER=维修师傅 */
    private String targetRole;

    /** 发布人 user_id */
    private Long publisherId;

    /** 发布时间，草稿状态下可为空 */
    private LocalDateTime publishTime;

    /** 是否置顶：0=否 1=是 */
    private Integer isTop;

    /** 状态：0=草稿 1=已发布 */
    private Integer status;

    /** 浏览次数 */
    private Integer viewCount;
}
