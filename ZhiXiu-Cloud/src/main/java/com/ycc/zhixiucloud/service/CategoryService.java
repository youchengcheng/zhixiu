package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.response.CategoryTreeResponseVO;

import java.util.List;

/**
 * 报修类型服务。
 *
 * <p>职责边界：只提供业主端/管理端共用的只读类型树，不包含类型维护入口
 * （类型属于基础配置数据，由管理端初始化或直接维护）。</p>
 *
 * <p>主要使用方：业主报修表单、AI 助手类型匹配、管理端工单筛选下拉框。</p>
 */
public interface CategoryService {

    /**
     * 查询启用的两级报修类型树。
     *
     * <p>一级节点 parent_id=0，二级节点挂在一级节点下；结果按 sort 升序，
     * 未启用的类型不会返回。该方法只读，无副作用。</p>
     *
     * @return 类型树（一级节点含 children）；无数据时返回空列表
     */
    List<CategoryTreeResponseVO> tree();
}
