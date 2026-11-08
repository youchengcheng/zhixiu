package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.entity.RepairCategory;
import com.ycc.zhixiucloud.entity.RepairKnowledge;
import com.ycc.zhixiucloud.entity.SlaConfig;
import com.ycc.zhixiucloud.entity.WorkOrder;
import com.ycc.zhixiucloud.mapper.RepairCategoryMapper;
import com.ycc.zhixiucloud.mapper.RepairKnowledgeMapper;
import com.ycc.zhixiucloud.mapper.SlaConfigMapper;
import com.ycc.zhixiucloud.mapper.WorkOrderMapper;
import com.ycc.zhixiucloud.request.CategorySaveRequestVO;
import com.ycc.zhixiucloud.response.AdminCategoryResponseVO;
import com.ycc.zhixiucloud.response.CategoryTreeResponseVO;
import com.ycc.zhixiucloud.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 报修类型服务实现。
 *
 * <p>公共树只返回启用节点；管理树返回全部节点。类型数据量小且维护动作需要立即生效，
 * 采用单次查询和内存组树，不引入缓存。删除前同时检查子节点、工单、SLA 和知识库引用，
 * 防止历史数据失去类型信息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 一级分类的父 ID */
    private static final long ROOT_PARENT_ID = 0L;

    /** 停用状态 */
    private static final int DISABLED = 0;

    /** 启用状态 */
    private static final int ENABLED = 1;

    private final RepairCategoryMapper repairCategoryMapper;

    private final WorkOrderMapper workOrderMapper;

    private final SlaConfigMapper slaConfigMapper;

    private final RepairKnowledgeMapper repairKnowledgeMapper;

    @Override
    public List<CategoryTreeResponseVO> tree() {
        // 公共树只查询启用节点，停用分类不会进入新建报修页面
        List<RepairCategory> all = repairCategoryMapper.selectList(Wrappers.<RepairCategory>lambdaQuery()
                .eq(RepairCategory::getStatus, ENABLED)
                .orderByAsc(RepairCategory::getSort)
                .orderByAsc(RepairCategory::getId));
        // 按父 ID 分组，避免逐个父节点重复查库
        Map<Long, List<RepairCategory>> childrenMap = groupChildren(all);
        // 组装只包含 id/name 的公共两级树
        return all.stream()
                .filter(this::isRoot)
                .map(root -> CategoryTreeResponseVO.builder()
                        .id(root.getId())
                        .name(root.getName())
                        .children(childrenMap.getOrDefault(root.getId(), List.of()).stream()
                                .map(child -> CategoryTreeResponseVO.builder()
                                        .id(child.getId())
                                        .name(child.getName())
                                        .build())
                                .toList())
                        .build())
                .toList();
    }

    @Override
    public List<AdminCategoryResponseVO> adminTree() {
        // 管理端必须看到停用节点，才能重新启用和继续维护
        List<RepairCategory> all = repairCategoryMapper.selectList(Wrappers.<RepairCategory>lambdaQuery()
                .orderByAsc(RepairCategory::getSort)
                .orderByAsc(RepairCategory::getId));
        // 一次查询后在内存中组装两级树
        Map<Long, List<RepairCategory>> childrenMap = groupChildren(all);
        return all.stream()
                .filter(this::isRoot)
                .map(root -> toAdminVO(root, childrenMap.getOrDefault(root.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySaveRequestVO requestVO) {
        // 校验父节点存在且不会形成第三级
        validateParent(null, requestVO.getParentId());
        // 同一父节点下不允许重名，避免前端出现无法区分的选项
        assertNameUnique(null, requestVO.getParentId(), requestVO.getName());
        // 新分类默认启用
        RepairCategory category = new RepairCategory();
        category.setName(requestVO.getName().trim());
        category.setParentId(requestVO.getParentId());
        category.setSort(requestVO.getSort());
        category.setIcon(requestVO.getIcon());
        category.setStatus(ENABLED);
        repairCategoryMapper.insert(category);
        log.info("新增报修类型：categoryId={}, parentId={}", category.getId(), category.getParentId());
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategorySaveRequestVO requestVO) {
        // 先读取原分类用于层级变更判断
        RepairCategory category = requireCategory(id);
        validateParent(id, requestVO.getParentId());
        assertNameUnique(id, requestVO.getParentId(), requestVO.getName());
        // 已有子节点或业务引用的分类不允许改变父节点，避免破坏两级结构和历史语义
        boolean parentChanged = !Objects.equals(category.getParentId(), requestVO.getParentId());
        if (parentChanged && (hasChildren(id) || isReferenced(id))) {
            throw BizException.of(ResultCode.CONFLICT, "该分类存在子分类或业务引用，不能调整所属层级");
        }
        // lambdaUpdate 允许显式把 icon 更新为 null，updateById 默认会忽略空值
        repairCategoryMapper.update(null, Wrappers.<RepairCategory>lambdaUpdate()
                .eq(RepairCategory::getId, id)
                .set(RepairCategory::getName, requestVO.getName().trim())
                .set(RepairCategory::getParentId, requestVO.getParentId())
                .set(RepairCategory::getSort, requestVO.getSort())
                .set(RepairCategory::getIcon, requestVO.getIcon())
                .set(RepairCategory::getUpdateTime, java.time.LocalDateTime.now()));
        log.info("修改报修类型：categoryId={}, parentId={}", id, requestVO.getParentId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        // Service 层保留状态防御，避免从非 Controller 场景传入非法值
        if (status == null || (status != DISABLED && status != ENABLED)) {
            throw BizException.of(ResultCode.PARAM_ERROR, "分类状态取值 0/1");
        }
        RepairCategory category = requireCategory(id);
        // 二级分类启用时要求父分类已启用，否则公共树仍无法展示该节点
        if (status == ENABLED && !isRoot(category)) {
            RepairCategory parent = repairCategoryMapper.selectById(category.getParentId());
            if (parent == null || !Integer.valueOf(ENABLED).equals(parent.getStatus())) {
                throw BizException.of(ResultCode.CONFLICT, "父分类未启用，不能启用该二级分类");
            }
        }
        // 只更新状态，不级联覆盖子分类原有启停设置
        RepairCategory update = new RepairCategory();
        update.setId(id);
        update.setStatus(status);
        repairCategoryMapper.updateById(update);
        log.info("报修类型状态调整：categoryId={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 分类不存在时返回明确的 404 业务错误
        requireCategory(id);
        // 一级分类仍有子节点时禁止删除，避免产生无父节点的二级分类
        if (hasChildren(id)) {
            throw BizException.of(ResultCode.CONFLICT, "该分类存在子分类，请先处理子分类");
        }
        // 任何历史或配置引用存在时只允许停用
        if (isReferenced(id)) {
            throw BizException.of(ResultCode.CONFLICT, "该分类已被业务数据引用，只能停用，不能删除");
        }
        // MyBatis-Plus 将其转换为 deleted=1 的逻辑删除
        repairCategoryMapper.deleteById(id);
        log.info("删除报修类型：categoryId={}", id);
    }

    /** 校验父节点存在并限制为两级结构。 */
    private void validateParent(Long currentId, Long parentId) {
        if (parentId == ROOT_PARENT_ID) {
            return;
        }
        if (currentId != null && currentId.equals(parentId)) {
            throw BizException.of(ResultCode.PARAM_ERROR, "分类不能以自身作为父分类");
        }
        RepairCategory parent = repairCategoryMapper.selectById(parentId);
        if (parent == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "父分类不存在");
        }
        if (!isRoot(parent)) {
            throw BizException.of(ResultCode.PARAM_ERROR, "系统只支持两级分类，父分类必须是一级分类");
        }
    }

    /** 校验同一父节点下的分类名称不重复。 */
    private void assertNameUnique(Long excludeId, Long parentId, String name) {
        LambdaQueryWrapper<RepairCategory> wrapper = Wrappers.<RepairCategory>lambdaQuery()
                .eq(RepairCategory::getParentId, parentId)
                .eq(RepairCategory::getName, name.trim())
                .ne(excludeId != null, RepairCategory::getId, excludeId);
        if (repairCategoryMapper.selectCount(wrapper) > 0) {
            throw BizException.of(ResultCode.CONFLICT, "同级分类名称已存在");
        }
    }

    /** 判断分类是否存在直接子节点。 */
    private boolean hasChildren(Long id) {
        return repairCategoryMapper.selectCount(Wrappers.<RepairCategory>lambdaQuery()
                .eq(RepairCategory::getParentId, id)) > 0;
    }

    /** 判断分类是否被工单、SLA 或知识库引用。 */
    private boolean isReferenced(Long id) {
        long orderCount = workOrderMapper.selectCount(Wrappers.<WorkOrder>lambdaQuery()
                .eq(WorkOrder::getCategoryId, id));
        long slaCount = slaConfigMapper.selectCount(Wrappers.<SlaConfig>lambdaQuery()
                .eq(SlaConfig::getCategoryId, id));
        long knowledgeCount = repairKnowledgeMapper.selectCount(Wrappers.<RepairKnowledge>lambdaQuery()
                .eq(RepairKnowledge::getCategoryId, id));
        return orderCount > 0 || slaCount > 0 || knowledgeCount > 0;
    }

    /** 查询分类并校验存在。 */
    private RepairCategory requireCategory(Long id) {
        RepairCategory category = repairCategoryMapper.selectById(id);
        if (category == null) {
            throw BizException.of(ResultCode.NOT_FOUND, "报修类型不存在");
        }
        return category;
    }

    /** 按父 ID 分组二级分类。 */
    private Map<Long, List<RepairCategory>> groupChildren(List<RepairCategory> all) {
        return all.stream()
                .filter(item -> !isRoot(item))
                .collect(Collectors.groupingBy(RepairCategory::getParentId));
    }

    /** 一级分类及其子节点转管理端树结构。 */
    private AdminCategoryResponseVO toAdminVO(RepairCategory root, List<RepairCategory> children) {
        return AdminCategoryResponseVO.builder()
                .id(root.getId())
                .name(root.getName())
                .parentId(root.getParentId())
                .sort(root.getSort())
                .icon(root.getIcon())
                .status(root.getStatus())
                .children(children.stream()
                        .sorted(Comparator.comparing(RepairCategory::getSort)
                                .thenComparing(RepairCategory::getId))
                        .map(child -> AdminCategoryResponseVO.builder()
                                .id(child.getId())
                                .name(child.getName())
                                .parentId(child.getParentId())
                                .sort(child.getSort())
                                .icon(child.getIcon())
                                .status(child.getStatus())
                                .build())
                        .toList())
                .build();
    }

    /** parentId=0 表示一级分类。 */
    private boolean isRoot(RepairCategory category) {
        return category.getParentId() != null && category.getParentId() == ROOT_PARENT_ID;
    }
}
