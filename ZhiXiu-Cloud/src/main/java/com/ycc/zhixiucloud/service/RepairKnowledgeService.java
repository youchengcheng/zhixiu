package com.ycc.zhixiucloud.service;

import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.request.KnowledgeFileRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeQueryRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeSaveRequestVO;
import com.ycc.zhixiucloud.request.KnowledgeTestSearchRequestVO;
import com.ycc.zhixiucloud.response.KnowledgeResponseVO;
import com.ycc.zhixiucloud.response.KnowledgeSearchResultResponseVO;

import java.util.List;

/**
 * 维修知识库服务：管理端 CRUD、异步向量化与检索测试。
 *
 * <p>职责边界：只维护知识条目及其向量，不参与 AI 对话流程；
 * 对话侧的 RAG 检索由 {@link AiAssistantService} 直接访问向量库。</p>
 *
 * <p>主要使用方：管理端知识库维护页面、AiTaskConsumer（异步向量化）。</p>
 *
 * <p>关键实现要点：</p>
 * <ul>
 *   <li>新增/修改后通过 RocketMQ 投递向量化任务，由 AiTaskConsumer 异步执行；</li>
 *   <li>修改会把条目标记为未向量化并递增版本号，向量化完成后再次回写标记；</li>
 *   <li>向量库 Bean 可能因未配置 AI 而缺失，所有向量操作均做判空降级，不阻断知识 CRUD；</li>
 *   <li>正文分片后写入向量库，分片 ID 清单用于完整覆盖和删除。</li>
 * </ul>
 */
public interface RepairKnowledgeService {

    /**
     * 知识库分页查询。
     *
     * <p>支持按关联报修类型精确筛选、按标题/内容关键字模糊筛选；
     * 结果按更新时间倒序并补充类型名称。</p>
     *
     * @param requestVO 分页与筛选参数
     * @return 知识分页结果
     */
    PageResult<KnowledgeResponseVO> page(KnowledgeQueryRequestVO requestVO);

    /**
     * 新增知识条目。
     *
     * <p>来源固定为人工录入（MANUAL），初始状态启用、未向量化、版本号 0；
     * 落库后立即投递异步向量化任务（MQ 投递在事务提交前后由生产者保证可靠性）。</p>
     *
     * @param requestVO 知识内容（标题、类型、正文、标签）
     * @return 新知识 ID
     */
    Long create(KnowledgeSaveRequestVO requestVO);

    /** 上传并解析知识文件，原文落库后异步分片向量化。 */
    Long createFromFile(KnowledgeFileRequestVO requestVO);

    /**
     * 修改知识条目。
     *
     * <p>内容变更后标记为未向量化、版本号 +1，并重新投递向量化任务；
     * 旧向量会在异步任务执行时先删后写。</p>
     *
     * @param id        知识 ID
     * @param requestVO 修改内容
     * @throws com.ycc.zhixiucloud.common.exception.BizException 知识条目不存在时抛出 404
     */
    void update(Long id, KnowledgeSaveRequestVO requestVO);

    /** 修改知识，上传文件时替换正文，否则保留原正文及来源。 */
    void updateFromFile(Long id, KnowledgeFileRequestVO requestVO);

    /**
     * 删除知识条目。
     *
     * <p>先逻辑删除数据库记录，再尝试删除向量库中的对应向量；
     * 向量删除失败只记日志，不阻断删除。</p>
     *
     * @param id 知识 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 知识条目不存在时抛出 404
     */
    void delete(Long id);

    /**
     * 手动触发重新向量化。
     *
     * <p>仅校验条目存在并投递 MQ 任务，实际向量化由 AiTaskConsumer 异步完成。</p>
     *
     * @param id 知识 ID
     * @throws com.ycc.zhixiucloud.common.exception.BizException 知识条目不存在时抛出 404
     */
    void revectorize(Long id);

    /**
     * 检索测试：用于管理端验证知识库召回效果。
     *
     * <p>对输入语句做向量相似度检索，返回命中的知识条目及相似度分数。
     * 该方法只读，不写向量库。</p>
     *
     * @param requestVO 检索参数（query 查询语句、topK 返回条数）
     * @return 命中的知识条目列表；无命中返回空列表
     * @throws com.ycc.zhixiucloud.common.exception.BizException AI 未启用
     *         （3001，向量库 Bean 不存在）或向量检索异常
     */
    List<KnowledgeSearchResultResponseVO> testSearch(KnowledgeTestSearchRequestVO requestVO);

    /**
     * 向量化单条知识，由 AiTaskConsumer 消费异步任务时调用。
     *
     * <p>流程：查条目 → 构建分片 Document → 删除全部旧向量 → 写入分片向量 → 回写
     * vectorized=1 与版本号。条目不存在或向量库不可用时只记日志并跳过，不抛异常，
     * 避免 MQ 反复重试。</p>
     *
     * @param knowledgeId 知识 ID
     */
    void vectorize(Long knowledgeId);
}
