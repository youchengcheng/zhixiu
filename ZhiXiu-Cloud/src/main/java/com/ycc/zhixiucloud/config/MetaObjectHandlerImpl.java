package com.ycc.zhixiucloud.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共字段自动填充处理器。
 *
 * <p>与 BaseEntity 上的 @TableField(fill = ...) 配合：INSERT 时填充 createTime、updateTime、
 * deleted（逻辑删除标记）、version（乐观锁初值）；UPDATE 时刷新 updateTime。
 * 数据库表虽有默认值，但显式填充后实体对象在插入/更新完成时即持有与库内一致的值，
 * 业务代码可直接复用（例如把 createTime 写入 Redis 缓存），无需回查数据库。</p>
 *
 * <p>strictXxxFill 只在实体存在对应属性且当前值为 null 时才写入：
 * 因此没有 version 字段的实体不会报错，也允许业务层按需显式指定字段值。</p>
 */
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    /** 新增填充：审计时间、逻辑删除标记、乐观锁版本号在此统一初始化。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(); // 同一次插入的创建/更新时间保持完全一致
        // 创建时间
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        // 更新时间：新增时与创建时间相同
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        // 逻辑删除标记 0=未删除，与 application.yml 的 logic-not-delete-value 对应
        strictInsertFill(metaObject, "deleted", Integer.class, 0);
        // 乐观锁版本号从 0 开始，与 MybatisPlusConfig 的乐观锁插件配合
        strictInsertFill(metaObject, "version", Integer.class, 0);
    }

    /** 更新填充：每次 UPDATE 自动刷新更新时间，业务代码无需手动设置。 */
    @Override
    public void updateFill(MetaObject metaObject) {
        // strictUpdateFill 仅在字段为 null 时生效，避免覆盖业务显式传入的 updateTime
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
