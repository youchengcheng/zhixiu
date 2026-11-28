package com.ycc.zhixiucloud.service.concierge;

import com.ycc.zhixiucloud.common.enums.AuditStatusEnum;
import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.common.enums.WorkStatusEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
import com.ycc.zhixiucloud.mapper.ConciergeQueryMapper;
import com.ycc.zhixiucloud.mapper.ConciergeQueryMapper.LoadScope;
import com.ycc.zhixiucloud.mapper.ConciergeQueryMapper.OrderCount;
import com.ycc.zhixiucloud.mapper.ConciergeQueryMapper.OrderScope;
import com.ycc.zhixiucloud.request.AdminOrderQueryRequestVO;
import com.ycc.zhixiucloud.request.OrderQueryRequestVO;
import com.ycc.zhixiucloud.request.PageRequestVO;
import com.ycc.zhixiucloud.request.WorkerQueryRequestVO;
import com.ycc.zhixiucloud.response.ConciergeResponse.Link;
import com.ycc.zhixiucloud.response.ConciergeResponse.Row;
import com.ycc.zhixiucloud.response.ConciergeResponse.Table;
import com.ycc.zhixiucloud.response.OrderDetailResponseVO;
import com.ycc.zhixiucloud.response.OrderListResponseVO;
import com.ycc.zhixiucloud.service.AnnouncementService;
import com.ycc.zhixiucloud.service.MaintenanceWorkerService;
import com.ycc.zhixiucloud.service.NotificationInboxService;
import com.ycc.zhixiucloud.service.WorkOrderLogService;
import com.ycc.zhixiucloud.service.WorkOrderService;
import com.ycc.zhixiucloud.service.concierge.ConciergeQueryIntent.DetailField;
import com.ycc.zhixiucloud.service.concierge.ConciergeQueryIntent.LoadFilter;
import com.ycc.zhixiucloud.service.concierge.ConciergeQueryIntent.Milestone;
import com.ycc.zhixiucloud.service.concierge.ConciergeQueryIntent.OrderFilter;
import com.ycc.zhixiucloud.service.concierge.ConciergeQueryIntent.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConciergeQueries {

    static final String UNSUPPORTED_ANSWER = "这个业务查询还未接入 AI 管家。";
    private static final int PAGE_SIZE = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern WORKER_CHANGES = Pattern.compile("接受派单|接下派单|确认接单|抢单|标记到场|到场签到|帮我签到|切换.*[上下]线|(?:帮我|我要|切到|设为|直接|现在)[上下]线|提交完工|申请转单|暂停工单");
    private static final Pattern ADMIN_CHANGES = Pattern.compile("通过.*审核|审核通过|(?:全部|批量)通过|驳回|发布.*公告|下架.*公告|(?:帮我|给他|直接|重新)派单|改派");
    private static final Pattern OWNER_CHANGES = Pattern.compile("确认完工|确认完成|给个好评|提交评价|取消.*工单|帮我报修|我要报修|如何报修|怎么报修|发起报修|生成.*草稿");
    private static final Pattern READ_REQUEST = Pattern.compile("^(?:(?:请|帮我|我想|麻烦你)\\s*)?(?:查|看|列出|显示|有哪些|有没有|目前|已发布)|(?:有哪些|有几条|还有多少|进度|历史|记录)[吗呢？?。\\s]*$");
    private static final List<String> ORDER_COLUMNS = List.of("工单号", "事项", "状态", "报修时间");

    private final WorkOrderService orders;
    private final WorkOrderLogService orderLogs;
    private final MaintenanceWorkerService workers;
    private final NotificationInboxService notifications;
    private final AnnouncementService announcements;
    private final ConciergeQueryMapper reports;

    Query existingQuery(String content, String role) {
        if (businessLink(content, role) != null) {
            return null;
        }
        if (content.contains("通知") || content.contains("未读消息")) {
            return new Query(Kind.NOTIFICATIONS, null, null, content.contains("未读") ? "UNREAD" : null);
        }
        if (content.contains("公告")) {
            return new Query(Kind.ANNOUNCEMENTS, null, null, null);
        }
        if (mentionsAudit(content)) {
            return new Query(Kind.PENDING_WORKERS, null, null, null);
        }
        if ("WORKER".equals(role) && (content.contains("在线状态") || content.contains("上下线")
                || content.matches(".*我.*(在线|离线|上线|下线|忙碌).*"))) {
            return new Query(Kind.WORK_STATUS, null, null, null);
        }
        if ("WORKER".equals(role) && (content.contains("待办") || content.contains("等我处理")
                || content.contains("我的任务"))) {
            return new Query(Kind.TODOS, null, null, null);
        }
        return null;
    }

    public Link businessLink(String content, String role) {
        if (READ_REQUEST.matcher(content).find()) {
            return null;
        }
        return switch (role) {
            case "OWNER" -> OWNER_CHANGES.matcher(content).find()
                    ? new Link(content.matches(".*(?:报修|草稿).*") ? "打开维修助手" : "查看我的工单",
                    content.matches(".*(?:报修|草稿).*") ? "/owner/ai-assistant" : "/owner/order/list") : null;
            case "ADMIN" -> ADMIN_CHANGES.matcher(content).find()
                    ? content.contains("公告") ? new Link("打开公告管理", "/admin/announcement")
                    : content.matches(".*(?:审核|通过|驳回).*") ? new Link("打开师傅管理", "/admin/worker")
                    : new Link("打开工单管理", "/admin/order/list") : null;
            case "WORKER" -> WORKER_CHANGES.matcher(content).find()
                    ? content.matches(".*(?:上线|下线).*") ? new Link("打开工作台", "/worker/workbench")
                    : content.contains("抢单") ? new Link("查看待接单", "/worker/pending")
                    : new Link("查看我的工单", "/worker/order/list") : null;
            default -> null;
        };
    }

    Link unavailableLink(String content, String role, String answer) {
        if (!UNSUPPORTED_ANSWER.equals(answer)) return null;
        if ("OWNER".equals(role) && content.contains("房产")) return new Link("打开我的房产", "/owner/property");
        if (("OWNER".equals(role) || "ADMIN".equals(role)) && content.contains("反馈")) {
            return new Link("打开反馈页面", path(role, "feedback"));
        }
        if ("ADMIN".equals(role) && content.contains("SLA")) return new Link("打开 SLA 管理", "/admin/sla");
        return null;
    }

    Link retryLink(String content, Long orderId, Query previous, String role) {
        if ("ADMIN".equals(role) && (content.contains("师傅") || previous != null && previous.kind() == Kind.WORKER_LOAD)
                && !content.contains("哪位师傅完成")) {
            return new Link("查看师傅管理", "/admin/worker");
        }
        if (orderId != null || content.matches(".*(?:工单|检修单|维修单|报修单|订单|评价|(?i:WO)[0-9]+).*")
                || previous != null && (previous.orderId() != null || previous.kind() == Kind.ORDER_REPORT)) {
            return new Link("查看工单列表", path(role, "order/list"));
        }
        return null;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Table execute(Query query, int page) {
        // 从登录上下文取角色。同一只读事务内完成统计和当前页查询，避免两个结果看到不同的数据版本。
        String role = SecurityUtils.currentRoleCode();
        return switch (query.kind()) {
            case ORDERS -> orderTable(query, role, page);
            case ORDER_REPORT -> orderReport(query, role, page);
            case WORKER_LOAD -> workerLoad(query.loadFilter(), page);
            case ORDER_DETAIL -> orderDetail(query.orderId(), role, page);
            case ORDER_LOOKUP -> orderLookup(query, role, page);
            case TODOS -> {
                requireRole("WORKER");
                yield workOrderRows("我的待办", orders.pageForWorkerTodos(SecurityUtils.requireWorkerId(),
                        page(new OrderQueryRequestVO(), page)), role, page);
            }
            case PENDING_WORKERS -> pendingWorkers(page);
            case WORK_STATUS -> workStatus(page);
            case NOTIFICATIONS -> notificationTable(role, page, "UNREAD".equals(query.status()));
            case ANNOUNCEMENTS -> announcementTable(role, page);
            case QUERY_CONTEXT, UNSUPPORTED -> throw BizException.of(ResultCode.PARAM_ERROR);
        };
    }

    private Table orderReport(Query query, String role, int page) {
        OrderFilter filter = query.orderFilter();
        Milestone milestone = filter.milestone();
        Period period = filter.period();
        // 一种业务状态可以对应多个数据库状态。例如「已完成」同时筛选已完工和已关单。
        var statuses = milestone != null ? milestone.statuses
                : query.status() == null ? List.<String>of() : List.of(query.status());
        var scope = orderScope(role, filter.mine(), query.orderId(), query.keyword(),
                filter.workerName(), statuses, milestone == null ? "CREATED" : milestone.dateField,
                period == null ? null : period.from().atStartOfDay(), period == null ? null : period.to().atStartOfDay());
        // 汇总先统计整个权限范围，表格的分页尺寸只影响下面取到的明细行数。
        List<OrderCount> counts = reports.orderCounts(scope);
        long total = counts.stream().mapToLong(OrderCount::total).sum();
        String label = milestone == null
                ? Objects.requireNonNullElse(OrderStatusEnum.nameOf(query.status()), "") : milestone.label;
        String periodText = period == null ? "" : period.from() + " 至 " + period.to().minusDays(1) + "，";
        String scopeText = filter.mine() && "WORKER".equals(role) ? "你实际接单的" : "当前权限内";
        String selection = (query.keyword() == null ? "" : "工单 " + query.keyword() + "，")
                + (filter.workerName() == null ? "" : "维修师傅为 " + filter.workerName() + "，");
        String summary = periodText + selection + scopeText + (total == 0 ? "没有匹配的" : "共有 " + total + " 张")
                + label + "工单";
        if (total > 0 && milestone == Milestone.COMPLETED) {
            summary += "，其中 " + count(counts, OrderStatusEnum.COMPLETED) + " 张待评价、"
                    + count(counts, OrderStatusEnum.CLOSED) + " 张已关单";
        }
        if (filter.grouped()) {
            // 表格按师傅分组时，用组数判断能否翻页，摘要中的总数仍是工单数。
            long groups = total == 0 ? 0 : reports.workerGroupCount(scope);
            var rows = total == 0 ? List.<Row>of() : reports.workerGroups(scope, PAGE_SIZE, offset(page)).stream()
                    .map(item -> row(null, workerName(item.workerName()), item.workerNo(), Long.toString(item.total())))
                    .toList();
            return table(label + "工单按维修师傅统计", List.of("实际维修师傅", "工号", "工单数"), rows,
                    groups, page, summary + (groups == 0 ? "。" : "。下表按实际接单师傅汇总。"));
        }
        // 明细与汇总共用同一个 scope。没有命中记录时直接返回空表格。
        var records = total == 0 ? List.<ConciergeQueryMapper.OrderRow>of()
                : reports.orderRows(scope, PAGE_SIZE, offset(page));
        if (records.size() == 1 && total == 1 && (query.orderId() != null || query.keyword() != null || milestone != null)) {
            var order = records.get(0);
            summary = periodText + "工单 " + order.orderNo() + "当前状态为"
                    + OrderStatusEnum.nameOf(order.status()) + "，实际维修师傅：" + workerName(order.workerName());
        }
        String dateLabel = milestone == null ? "报修时间" : milestone.dateLabel;
        var rows = records.stream().map(item -> row(path(role, "order/" + item.id()), item.orderNo(), item.title(),
                workerName(item.workerName()), OrderStatusEnum.nameOf(item.status()), date(item.activityTime()))).toList();
        return table(label + "工单", List.of("工单号", "事项", "实际维修师傅", "状态", dateLabel),
                rows, total, page, summary + "。");
    }

    private Table workerLoad(LoadFilter filter, int page) {
        // 负载工具需要管理员权限，在读取师傅信息前先拦截其他角色。
        requireRole("ADMIN");
        WorkStatusEnum status = filter.workStatus();
        var scope = new LoadScope(AuditStatusEnum.PASSED.getCode(), WorkStatusEnum.DISABLED.getCode(),
                filter.zeroLoad(), filter.noPending(), status == null ? null : status.getCode());
        // SQL 用同一组条件统计状态分布，并用相同条件取当前页师傅。
        var counts = reports.loadCounts(scope);
        long total = counts.stream().mapToLong(ConciergeQueryMapper.LoadCount::total).sum();
        String title = filter.noPending() ? "手上没有工单的师傅" : filter.zeroLoad() ? "零负载师傅" : "师傅负载";
        String condition = filter.noPending() ? "在手工单和待接派单都为 0" : filter.zeroLoad() ? "在手工单为 0" : "已审核可用";
        if (status != null) condition += "、状态为" + status.getName();
        String summary = total == 0 ? "当前没有符合「" + condition + "」条件的师傅。"
                : "当前有 " + total + " 位师傅符合「" + condition + "」，其中"
                + counts.stream().map(item -> " " + item.total() + " 位" + statusLabel(item.workStatus()))
                .collect(Collectors.joining("、")) + "。";
        var rows = total == 0 ? List.<Row>of() : reports.loadRows(scope, PAGE_SIZE, offset(page)).stream()
                .map(item -> row("/admin/worker", workerName(item.workName()), item.workerNo(),
                        statusLabel(item.workStatus()), Integer.toString(item.currentLoad()), Long.toString(item.pending())))
                .toList();
        return table(title, List.of("师傅", "工号", "工作状态", "在手工单", "待接派单"), rows, total, page, summary);
    }

    private static long count(List<OrderCount> counts, OrderStatusEnum state) {
        return counts.stream().filter(item -> state.getCode().equals(item.status()))
                .mapToLong(OrderCount::total).sum();
    }

    private static String workerName(String name) {
        return StringUtils.hasText(name) ? name : "未记录";
    }

    private static String statusLabel(int code) {
        WorkStatusEnum status = WorkStatusEnum.of(code);
        return status == null ? "状态未记录" : status.getName();
    }

    private static long offset(int page) {
        return (long) (page - 1) * PAGE_SIZE;
    }

    private Table orderTable(Query query, String role, int page) {
        var request = page(new AdminOrderQueryRequestVO(), page);
        request.setStatus(query.status());
        request.setKeyword(query.keyword());
        var result = switch (role) {
            case "ADMIN" -> orders.pageForAdmin(request);
            case "OWNER" -> orders.pageForOwner(request);
            case "WORKER" -> orders.pageForWorker(SecurityUtils.requireWorkerId(), request);
            default -> throw BizException.of(ResultCode.FORBIDDEN);
        };
        return workOrderRows("ADMIN".equals(role) ? "工单查询" : "我的工单", result, role, page);
    }

    private Table workOrderRows(String title, PageResult<OrderListResponseVO> result, String role, int page) {
        return table(title, ORDER_COLUMNS, result.getRecords().stream()
                .map(item -> row(path(role, "order/" + item.getId()), item.getOrderNo(), item.getTitle(),
                        item.getStatusText(), date(item.getCreateTime()))).toList(), result.getTotal(), page,
                result.getTotal() == 0 ? "当前没有符合条件的工单。" : title + "匹配 " + result.getTotal() + " 张工单。");
    }

    private Table orderDetail(Long id, String role, int page) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_ERROR, "请在工单详情页提问，或直接提供工单号");
        }
        OrderDetailResponseVO order = authorizedDetail(id, role);
        return orderProgress(id, role, page, order);
    }

    private Table orderLookup(Query query, String role, int page) {
        if (query.orderId() == null && !StringUtils.hasText(query.keyword())) {
            throw BizException.of(ResultCode.PARAM_ERROR, "请提供要查询的工单号");
        }
        // 先在登录角色的可见范围内精确定位单张工单，再取评价等关联信息。
        // 维修人沿用按 accepted_worker_id 的组合查询，真实评价由各角色的工单详情服务返回。
        var scope = orderScope(role, false, query.orderId(), query.keyword(), null, List.of(), "CREATED", null, null);
        var order = reports.orderRows(scope, 1, 0).stream().findFirst()
                .orElseThrow(() -> BizException.of(ResultCode.NOT_FOUND, "未找到当前账号可查看的工单"));
        List<DetailField> fields = query.detailFields();
        // 只问进度时复用工单时间线，组合问题则按请求字段组装一行详情。
        if (fields.equals(List.of(DetailField.PROGRESS))) {
            return orderProgress(order.id(), role, page, authorizedDetail(order.id(), role));
        }
        List<String> columns = new ArrayList<>(List.of("工单号", "状态"));
        List<String> values = new ArrayList<>(List.of(order.orderNo(), OrderStatusEnum.nameOf(order.status())));
        StringBuilder summary = new StringBuilder("工单 ").append(order.orderNo()).append(" 当前状态为「")
                .append(OrderStatusEnum.nameOf(order.status())).append("」。");
        if (fields.contains(DetailField.ACTUAL_WORKER)) {
            String name = workerName(order.workerName());
            columns.add("实际维修师傅");
            values.add(name);
            summary.append("实际维修师傅为「").append(name).append("」。");
        }
        if (fields.contains(DetailField.EVALUATION)) {
            // 直接根据该工单读取真实评价记录。评分和原文用于表格，摘要的事实句只含评价状态和评分。
            var evaluation = authorizedDetail(order.id(), role).getEvaluation();
            columns.addAll(List.of("评分", "评语"));
            if (evaluation == null) {
                values.add("暂无评价");
                values.add("—");
                summary.append("该工单暂无评价。");
            } else {
                String rating = evaluation.getScore() == null ? "未记录" : evaluation.getScore() + " 星";
                values.add(rating);
                values.add(Objects.toString(evaluation.getContent(), "—"));
                summary.append("该工单已有评价，评分为 ").append(rating).append("。");
            }
        }
        var rows = page == 1 ? List.of(new Row(values, path(role, "order/" + order.id()))) : List.<Row>of();
        return table("工单详情", columns, rows, 1, page, summary.toString());
    }

    private static OrderScope orderScope(String role, boolean mine, Long orderId, String orderNo, String workerName,
                                         List<String> statuses, String dateField, LocalDateTime from, LocalDateTime to) {
        // 角色与账号 ID 来源于服务端登录态，明细、人数和统计都用这个范围执行。
        return switch (role) {
            case "OWNER" -> new OrderScope(SecurityUtils.currentUserId(), null, false, orderId, orderNo,
                    workerName, statuses, dateField, from, to);
            case "WORKER" -> new OrderScope(null, SecurityUtils.requireWorkerId(), mine, orderId, orderNo,
                    workerName, statuses, dateField, from, to);
            case "ADMIN" -> new OrderScope(null, null, false, orderId, orderNo, workerName, statuses, dateField, from, to);
            default -> throw BizException.of(ResultCode.FORBIDDEN);
        };
    }

    private OrderDetailResponseVO authorizedDetail(Long id, String role) {
        return switch (role) {
            case "OWNER" -> orders.getDetailForOwner(id);
            case "ADMIN" -> orders.getDetailForAdmin(id);
            case "WORKER" -> orders.getAssignedDetailForWorker(id);
            default -> throw BizException.of(ResultCode.FORBIDDEN);
        };
    }

    private Table orderProgress(Long id, String role, int page, OrderDetailResponseVO order) {
        List<Row> rows = new ArrayList<>();
        String orderPath = path(role, "order/" + id);
        var logs = orderLogs.listByOrder(id);
        var lastUpdate = logs.isEmpty() ? null : logs.get(logs.size() - 1).getCreateTime();
        rows.add(row(orderPath, date(lastUpdate), "当前状态", order.getStatusText()));
        logs.forEach(item -> rows.add(row(null, date(item.getCreateTime()),
                item.getActionText(), item.getToStatusText())));
        int from = (int) Math.min(rows.size(), ((long) page - 1) * PAGE_SIZE);
        return table(order.getOrderNo() + " · " + order.getTitle(), List.of("时间", "事项", "状态"),
                rows.subList(from, Math.min(rows.size(), from + PAGE_SIZE)), rows.size(), page,
                "工单 " + order.getOrderNo() + " 当前状态为「" + order.getStatusText() + "」，共有 "
                        + logs.size() + " 条流转记录。");
    }

    private Table pendingWorkers(int page) {
        requireRole("ADMIN");
        WorkerQueryRequestVO request = page(new WorkerQueryRequestVO(), page);
        request.setAuditStatus(0);
        var result = workers.page(request);
        return table("待审核师傅", List.of("工号", "师傅", "工种", "审核状态"), result.getRecords().stream()
                .map(item -> row("/admin/worker", item.getWorkerNo(), item.getRealName(), item.getSkillTags(),
                        item.getAuditStatusText())).toList(), result.getTotal(), page,
                result.getTotal() == 0 ? "当前没有待审核的师傅。" : "当前有 " + result.getTotal() + " 位师傅等待审核。");
    }

    private Table workStatus(int page) {
        requireRole("WORKER");
        var worker = workers.requireCurrentWorker();
        WorkStatusEnum status = WorkStatusEnum.of(worker.getWorkStatus());
        var rows = page == 1 ? List.of(row("/worker/workbench", worker.getWorkerNo(),
                status == null ? null : status.getName())) : List.<Row>of();
        return table("当前工作状态", List.of("工号", "状态"), rows, 1, page,
                "你当前的工作状态是「" + (status == null ? "未记录" : status.getName()) + "」。");
    }

    private Table notificationTable(String role, int page, boolean unreadOnly) {
        var result = notifications.page(page(new PageRequestVO(), page), unreadOnly);
        String title = unreadOnly ? "我的未读通知" : "我的通知";
        String summary = result.getTotal() == 0 ? (unreadOnly ? "你当前没有未读通知。" : "你当前没有通知。")
                : unreadOnly ? "你有 " + result.getTotal() + " 条未读通知。"
                : "你共有 " + result.getTotal() + " 条通知，其中 " + notifications.unreadCount().count() + " 条未读。";
        return table(title, List.of("标题", "状态", "时间"), result.getRecords().stream()
                .map(item -> row(path(role, "notification"), item.getTitle(),
                        Integer.valueOf(1).equals(item.getIsRead()) ? "已读" : "未读", date(item.getCreateTime())))
                .toList(), result.getTotal(), page, summary);
    }

    private Table announcementTable(String role, int page) {
        var result = announcements.pagePublishedForRole(page(new PageRequestVO(), page), role);
        return table("已发布公告", List.of("标题", "内容摘要", "发布时间"), result.getRecords().stream()
                .map(item -> row(path(role, "announcement"), item.getTitle(), summarize(item.getContent()),
                        date(item.getPublishTime()))).toList(), result.getTotal(), page,
                result.getTotal() == 0 ? "当前没有向你的角色发布的公告。"
                        : "当前向你的角色发布了 " + result.getTotal() + " 条公告。");
    }

    private static String summarize(String content) {
        if (content == null || content.length() <= 90) return content;
        int end = content.offsetByCodePoints(0, Math.min(90, content.codePointCount(0, content.length())));
        return content.substring(0, end) + "…";
    }

    private static boolean mentionsAudit(String content) {
        return content.contains("待审核") || content.contains("待审师傅")
                || content.matches(".*(?:师傅.*审核|审核.*师傅).*");
    }

    private static String path(String role, String suffix) {
        return "/" + role.toLowerCase(Locale.ROOT) + "/" + suffix;
    }

    private static String date(LocalDateTime time) {
        return time == null ? null : time.format(DATE);
    }

    private static Row row(String path, String... cells) {
        return new Row(Arrays.stream(cells).map(value -> Objects.requireNonNullElse(value, "—")).toList(), path);
    }

    private static Table table(String title, List<String> columns, List<Row> rows, long total, int page, String summary) {
        return new Table(title, columns, rows, total, page, (long) page * PAGE_SIZE < total, summary);
    }

    private static <T extends PageRequestVO> T page(T request, int number) {
        request.setPageNum(number);
        request.setPageSize(PAGE_SIZE);
        return request;
    }

    private static void requireRole(String role) {
        if (!role.equals(SecurityUtils.currentRoleCode())) {
            throw BizException.of(ResultCode.FORBIDDEN);
        }
    }

    public enum Kind {
        ORDERS, ORDER_DETAIL, TODOS, PENDING_WORKERS, WORK_STATUS, NOTIFICATIONS, ANNOUNCEMENTS,
        ORDER_REPORT, WORKER_LOAD, ORDER_LOOKUP, QUERY_CONTEXT, UNSUPPORTED
    }

    public record Query(Kind kind, Long orderId, String keyword, String status, OrderFilter orderFilter,
                        LoadFilter loadFilter, List<DetailField> detailFields) {

        Query(Kind kind, Long orderId, String keyword, String status, OrderFilter orderFilter, LoadFilter loadFilter) {
            this(kind, orderId, keyword, status, orderFilter, loadFilter, null);
        }

        Query(Kind kind, Long orderId, String keyword, String status) {
            this(kind, orderId, keyword, status, null, null);
        }

    }
}
