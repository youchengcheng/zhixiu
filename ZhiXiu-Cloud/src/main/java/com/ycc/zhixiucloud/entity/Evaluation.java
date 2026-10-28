package com.ycc.zhixiucloud.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评价表 evaluation：一单一评。
 * <p>
 * 业主在工单完成后对维修师傅进行评分与文字评价，包含总分及响应速度、
 * 服务态度、维修质量三个分项，用于师傅评分统计与满意度分析。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation")
public class Evaluation extends BaseEntity {

    /** 工单 ID（work_order.id），同一工单仅允许一条评价 */
    private Long orderId;

    /** 评价人（业主）user_id */
    private Long ownerId;

    /** 师傅 ID（maintenance_worker.id） */
    private Long workerId;

    /** 总分 1-5 */
    private Integer score;

    /** 响应速度评分 1-5 */
    private Integer speedScore;

    /** 服务态度评分 1-5 */
    private Integer attitudeScore;

    /** 维修质量评分 1-5 */
    private Integer qualityScore;

    /** 评价文字内容，可为空 */
    private String content;

    /** 评价图片 URL，逗号分隔 */
    private String images;
}
