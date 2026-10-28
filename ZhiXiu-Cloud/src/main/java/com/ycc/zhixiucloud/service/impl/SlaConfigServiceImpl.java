package com.ycc.zhixiucloud.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.entity.SlaConfig;
import com.ycc.zhixiucloud.mapper.SlaConfigMapper;
import com.ycc.zhixiucloud.request.SlaConfigRequestVO;
import com.ycc.zhixiucloud.response.SlaConfigResponseVO;
import com.ycc.zhixiucloud.service.SlaConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SLA 配置服务实现：管理端规则增删改查与启停。
 *
 * <p>四个匹配维度（类型 / 紧急程度 / 安全标记 / 影响范围）全部为空即全局兜底规则；
 * 兜底规则允许修改但禁止删除，作为默认匹配项保留。新建规则默认启用，
 * priorityWeight 为空时按 0 处理；响应/处理时限为空时不在此处补值，由
 * {@code SlaServiceImpl} 在计算超时点时回退到配置项默认时限。</p>
 *
 * <p>本服务不维护规则缓存：{@code SlaServiceImpl.matchSla} 每次实时查询启用规则，
 * 因此新增、修改、启停、删除都会立即生效；若后续引入缓存，需在本类写操作提交后统一失效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlaConfigServiceImpl implements SlaConfigService {

    /** 启用状态 */
    private static final int ENABLED = 1;

    /** 禁用状态 */
    private static final int DISABLED = 0;

    private final SlaConfigMapper slaConfigMapper;

    /**
     * 查询全部 SLA 规则。
     *
     * <p>按派单权重降序、id 升序返回，权重相同保持创建顺序，便于管理端稳定展示。</p>
     *
     * @return 规则展示 VO 列表
     */
    @Override
    public List<SlaConfigResponseVO> list() {
        // 按权重降序、id 升序查询并转换为 VO
        return slaConfigMapper.selectList(Wrappers.<SlaConfig>lambdaQuery()
                        .orderByDesc(SlaConfig::getPriorityWeight)
                        .orderByAsc(SlaConfig::getId)).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 新增 SLA 规则。
     *
     * <p>新规则默认启用；四个匹配维度可为空，全空时即为全局兜底规则。priorityWeight 为空
     * 时由 {@link #fill} 兜底为 0。</p>
     *
     * @param requestVO 规则各维度、响应/处理时限、升级对象与派单权重
     * @return 新规则主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SlaConfigRequestVO requestVO) {
        // 新建规则实体
        SlaConfig config = new SlaConfig();
        // 复制请求字段，权重为空时兜底为 0
        fill(config, requestVO);
        // 新规则默认启用
        config.setEnabled(ENABLED);
        // 插入落库
        slaConfigMapper.insert(config);
        // 记录新增日志
        log.info("新增 SLA 规则：id={}, categoryId={}, urgency={}, safetyFlag={}, affectScope={}",
                config.getId(), config.getCategoryId(), config.getUrgency(), config.getSafetyFlag(),
                config.getAffectScope());
        // 返回新规则主键
        return config.getId();
    }

    /**
     * 修改 SLA 规则。
     *
     * <p>除 priorityWeight 空值会按 0 处理外，其余字段的 null 值会被 MyBatis-Plus 忽略，
     * 不会清空原值。启用状态不在此处修改，需调用 {@link #updateEnabled(Long, Integer)}。
     * 规则修改后下一次匹配立即生效（无缓存）。</p>
     *
     * @param id        规则主键
     * @param requestVO 新的规则配置
     * @throws BizException 规则不存在时抛出 404 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SlaConfigRequestVO requestVO) {
        // 先校验规则存在
        SlaConfig exists = requireConfig(id);
        // 只组装主键与待修改字段，null 字段会被 MyBatis-Plus 忽略
        SlaConfig config = new SlaConfig();
        // 指定主键
        config.setId(exists.getId());
        // 复制请求字段，权重为空时兜底为 0
        fill(config, requestVO);
        // 按主键更新非空字段
        slaConfigMapper.updateById(config);
        // 记录修改日志
        log.info("修改 SLA 规则：id={}", id);
    }

    /**
     * 启用或禁用 SLA 规则。
     *
     * <p>禁用后 {@code matchSla} 不再查询到该规则，对后续新工单立即生效；已按旧规则生成
     * 超时点的存量工单不受影响。</p>
     *
     * @param id      规则主键
     * @param enabled 1 启用、0 禁用
     * @throws BizException 规则不存在时抛出 404；取值不是 0/1 时抛出参数错误
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Integer enabled) {
        // 先校验规则存在
        requireConfig(id);
        if (enabled == null || (enabled != ENABLED && enabled != DISABLED)) {
            // 只接受 0/1 两种取值
            throw BizException.of(ResultCode.PARAM_ERROR, "启用状态取值 0/1");
        }
        // 只组装主键与启用状态，避免覆盖其它字段
        SlaConfig config = new SlaConfig();
        // 指定主键
        config.setId(id);
        // 写入目标启用状态
        config.setEnabled(enabled);
        // 只更新启用状态字段
        slaConfigMapper.updateById(config);
        // 记录状态调整日志
        log.info("SLA 规则状态调整：id={}, enabled={}", id, enabled);
    }

    /**
     * 删除 SLA 规则。
     *
     * <p>全局兜底规则（四个匹配维度全为空）不允许删除，避免误删后失去默认匹配规则；
     * 具体规则删除后立即从匹配范围中移除。</p>
     *
     * @param id 规则主键
     * @throws BizException 规则不存在时抛出 404；规则为全局兜底规则时抛出参数错误
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 先取出规则用于兜底判断
        SlaConfig exists = requireConfig(id);
        if (isFallback(exists)) {
            // 全局兜底规则受保护，避免误删后失去默认匹配
            throw BizException.of(ResultCode.PARAM_ERROR, "全局兜底规则不可删除");
        }
        // 按主键删除规则
        slaConfigMapper.deleteById(id);
        // 记录删除日志
        log.info("删除 SLA 规则：id={}", id);
    }

    /**
     * 将请求字段复制到实体，并处理派单权重的默认值。
     *
     * @param config    待填充的规则实体
     * @param requestVO 管理端请求
     */
    private void fill(SlaConfig config, SlaConfigRequestVO requestVO) {
        // 复制报修类型维度
        config.setCategoryId(requestVO.getCategoryId());
        // 复制紧急程度维度
        config.setUrgency(requestVO.getUrgency());
        // 复制安全标记维度
        config.setSafetyFlag(requestVO.getSafetyFlag());
        // 复制影响范围维度
        config.setAffectScope(requestVO.getAffectScope());
        // 复制响应时限
        config.setResponseTimeout(requestVO.getResponseTimeout());
        // 复制处理时限
        config.setHandleTimeout(requestVO.getHandleTimeout());
        // 复制超时升级对象
        config.setEscalateTo(requestVO.getEscalateTo());
        // 派单权重为空时按 0 处理
        config.setPriorityWeight(requestVO.getPriorityWeight() == null ? 0 : requestVO.getPriorityWeight());
    }

    /**
     * 查询规则并校验存在。
     *
     * @param id 规则主键
     * @return 规则实体
     * @throws BizException 规则不存在时抛出 404 业务异常
     */
    private SlaConfig requireConfig(Long id) {
        // 按主键查询规则
        SlaConfig config = slaConfigMapper.selectById(id);
        // 查询为空说明规则不存在
        if (config == null) {
            // 不存在统一抛 404
            throw BizException.of(ResultCode.NOT_FOUND, "SLA 规则不存在");
        }
        // 返回规则实体
        return config;
    }

    /**
     * 判断是否为全局兜底规则：四个匹配维度全部为空。
     *
     * @param config 规则实体
     * @return true 表示兜底规则
     */
    private boolean isFallback(SlaConfig config) {
        // 四个匹配维度全空即全局兜底规则
        return config.getCategoryId() == null && config.getUrgency() == null
                && config.getSafetyFlag() == null && config.getAffectScope() == null;
    }

    /**
     * 规则实体转展示 VO。
     *
     * @param config 规则实体
     * @return 规则展示 VO
     */
    private SlaConfigResponseVO toVO(SlaConfig config) {
        // 装配规则展示对象
        return SlaConfigResponseVO.builder()
                .id(config.getId())
                .categoryId(config.getCategoryId())
                .urgency(config.getUrgency())
                .safetyFlag(config.getSafetyFlag())
                .affectScope(config.getAffectScope())
                .responseTimeout(config.getResponseTimeout())
                .handleTimeout(config.getHandleTimeout())
                .escalateTo(config.getEscalateTo())
                .priorityWeight(config.getPriorityWeight())
                .enabled(config.getEnabled())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}