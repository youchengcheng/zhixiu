package com.ycc.zhixiucloud.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页响应体。
 *
 * <p>统一封装 MyBatis-Plus 的分页结果，固定输出 total、pages、current、size、records
 * 五个字段，前端分页组件可直接消费。提供两种构造方式：实体分页直接转换，
 * 以及“先按实体分页、再逐条映射为 VO”的转换方式。</p>
 */
@Getter
@Schema(description = "分页响应体")
public class PageResult<T> implements Serializable {

    @Schema(description = "总条数")
    private final long total;

    @Schema(description = "总页数")
    private final long pages;

    @Schema(description = "当前页")
    private final long current;

    @Schema(description = "每页条数")
    private final long size;

    @Schema(description = "当前页数据")
    private final List<T> records;

    private PageResult(long total, long pages, long current, long size, List<T> records) {
        // 总条数
        this.total = total;
        // 总页数
        this.pages = pages;
        // 当前页
        this.current = current;
        // 每页条数
        this.size = size;
        // 数据为空时用空列表兜底
        this.records = records == null ? Collections.emptyList() : records;
    }

    /**
     * 将 MyBatis-Plus 分页对象直接转换为统一分页响应。
     *
     * @param page 分页查询结果
     * @param <T>  记录类型
     * @return 分页响应体
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        // 直接复制分页字段构造响应
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(),
                page.getRecords());
    }

    /**
     * 分页对象与出参类型不同时使用：按实体完成分页后，再逐条转换为目标类型。
     *
     * @param page   实体分页结果
     * @param mapper 实体到出参的转换函数
     * @param <T>    出参类型
     * @param <E>    实体类型
     * @return 转换后的分页响应体
     */
    public static <T, E> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        // 逐条应用转换函数，空结果用空列表兜底
        List<T> records = page.getRecords() == null ? Collections.emptyList()
                : page.getRecords().stream().map(mapper).toList();
        // 用转换后的记录构造分页响应
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 构造空分页结果，用于查询无数据但仍需保留分页参数的场景。
     *
     * @param current 当前页
     * @param size    每页条数
     * @param <T>     记录类型
     * @return 总条数与总页数均为 0 的分页响应体
     */
    public static <T> PageResult<T> empty(long current, long size) {
        // 总条数与总页数均置零，保留分页参数
        return new PageResult<>(0L, 0L, current, size, Collections.emptyList());
    }
}