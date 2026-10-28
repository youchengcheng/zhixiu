package com.ycc.zhixiucloud.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 拦截器配置。
 *
 * <p>注册两个内部拦截器：</p>
 * <ul>
 *   <li>分页插件：按 MySQL 方言生成 LIMIT 语句，并限制单页最大条数；</li>
 *   <li>乐观锁插件：识别实体上的 @Version 字段（如 WorkOrder.version），UPDATE 时自动附加
 *       版本号条件并自增，防止并发更新相互覆盖，是 Redisson 分布式锁之外的第二道防线。</li>
 * </ul>
 *
 * <p>插件顺序不可调换：分页插件需在乐观锁插件之前处理 SQL。</p>
 */
@Configuration
public class MybatisPlusConfig {

    /** 单页最大条数，防止前端传入超大 pageSize 拖垮数据库 */
    private static final long MAX_LIMIT = 200L;

    /**
     * 构建并注册 MyBatis-Plus 拦截器链。
     * 同一链路上的插件按添加顺序依次改写 SQL。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建 MyBatis-Plus 拦截器链
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 指定 MySQL 方言，生成 LIMIT/OFFSET 形式的分页 SQL
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最多 200 条，避免 pageSize 被恶意放大导致全表扫描
        pagination.setMaxLimit(MAX_LIMIT);
        // 页码越界时不折返第一页，直接返回空结果，避免前端翻页出现重复数据
        pagination.setOverflow(false);
        // 分页插件必须位于乐观锁插件之前
        interceptor.addInnerInterceptor(pagination);
        // 乐观锁插件：配合实体 @Version 字段与 MetaObjectHandler 填充的初始版本号
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 返回组装完成的拦截器链
        return interceptor;
    }
}
