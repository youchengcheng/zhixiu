package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.entity.RepairCategory;
import com.ycc.zhixiucloud.mapper.RepairCategoryMapper;
import com.ycc.zhixiucloud.response.CategoryTreeResponseVO;
import com.ycc.zhixiucloud.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报修类型服务实现。
 *
 * <p>数据结构：一级分类 parentId=0，二级分类指向一级 ID；服务只返回启用（status=1）的分类。</p>
 *
 * <p>树构建：一次查询全部分类后，按 parentId 在内存分组，再挂到对应一级节点下，
 * 避免逐级递归查库。分类数据量小、变更频率低，这里采用「单次查询 + 内存组装」的轻量实现，
 * 服务层未额外引入缓存；若后续分类数量增大，可在此方法上增加缓存并随分类变更失效。</p>
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final long ROOT_PARENT_ID = 0L;

    private final RepairCategoryMapper repairCategoryMapper;

    /**
     * 构建启用中的报修类型树。
     *
     * <p>步骤：查询全部启用分类并按 sort 排序 → 以 parentId 分组得到「父 ID → 子列表」→
     * 筛选一级节点，逐个挂载其子分类并保持排序。返回结构只包含展示所需 id/name，
     * 前端可直接用于级联选择。</p>
     *
     * @return 两级分类树；无启用分类时返回空列表
     */
    @Override
    public List<CategoryTreeResponseVO> tree() {
        // 一次查询全部启用分类并按 sort 升序
        List<RepairCategory> all = repairCategoryMapper.selectList(Wrappers.<RepairCategory>lambdaQuery()
                .eq(RepairCategory::getStatus, 1)
                .orderByAsc(RepairCategory::getSort));

        // 按 parentId 分组得到「父 ID → 子列表」
        Map<Long, List<RepairCategory>> childrenMap = all.stream()
                .filter(item -> !isRoot(item))
                .collect(Collectors.groupingBy(RepairCategory::getParentId));

        // 筛选一级节点并挂载子分类，组装两级树
        return all.stream()
                .filter(this::isRoot)
                .sorted(Comparator.comparing(RepairCategory::getSort))
                .map(root -> CategoryTreeResponseVO.builder()
                        .id(root.getId())
                        .name(root.getName())
                        .children(childrenMap.getOrDefault(root.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(RepairCategory::getSort))
                                .map(child -> CategoryTreeResponseVO.builder()
                                        .id(child.getId())
                                        .name(child.getName())
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    /**
     * 判断是否为一级分类。
     *
     * @param category 分类实体
     * @return parentId 非空且等于 0 时返回 true
     */
    private boolean isRoot(RepairCategory category) {
        // parentId 非空且等于 0 即一级分类
        return category.getParentId() != null && category.getParentId() == ROOT_PARENT_ID;
    }
}
