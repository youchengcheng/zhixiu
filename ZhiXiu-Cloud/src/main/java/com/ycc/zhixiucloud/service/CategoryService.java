package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.response.CategoryTreeResponseVO;
import com.ycc.zhixiucloud.request.CategorySaveRequestVO;
import com.ycc.zhixiucloud.response.AdminCategoryResponseVO;

import java.util.List;

/**
 * 报修类型服务。
 *
 * <p>职责边界：公共接口提供启用中的类型树；管理端接口提供包含停用节点的完整树，
 * 并负责两级类型的新增、修改、排序、启停与安全删除。</p>
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

    /** 查询管理端完整类型树，包含停用节点。 */
    List<AdminCategoryResponseVO> adminTree();

    /** 新增一级或二级报修类型。 */
    Long create(CategorySaveRequestVO requestVO);

    /** 修改报修类型基础信息及层级。 */
    void update(Long id, CategorySaveRequestVO requestVO);

    /** 启用或停用报修类型。 */
    void updateStatus(Long id, Integer status);

    /** 删除未被引用且没有子节点的报修类型。 */
    void delete(Long id);
}
