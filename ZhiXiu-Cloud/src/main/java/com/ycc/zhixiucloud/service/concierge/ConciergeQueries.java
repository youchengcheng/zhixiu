package com.ycc.zhixiucloud.service.concierge;

import com.ycc.zhixiucloud.common.enums.WorkStatusEnum;
import com.ycc.zhixiucloud.common.exception.BizException;
import com.ycc.zhixiucloud.common.result.PageResult;
import com.ycc.zhixiucloud.common.result.ResultCode;
import com.ycc.zhixiucloud.common.util.SecurityUtils;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ConciergeQueries {

    private static final int PAGE_SIZE = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern ORDER_NUMBER = Pattern.compile("(?i)(?<![A-Z0-9])WO[0-9]{6,24}(?![A-Z0-9])|工单号\\s*[:：]?\\s*([A-Z0-9-]{6,})");
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

    public Query match(String content, Long orderId, String role) {
        if (businessLink(content, role) != null) {
            return null;
        }
        if (content.contains("通知") || content.contains("未读消息")) {
            return new Query(Kind.NOTIFICATIONS, null, null, null);
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
        Matcher number = ORDER_NUMBER.matcher(content);
        if (number.find()) {
            String keyword = Objects.requireNonNullElse(number.group(1), number.group()).toUpperCase(Locale.ROOT);
            return new Query(Kind.ORDERS, null, keyword, null);
        }
        boolean individual = content.matches(".*(?:这[张个笔单]|当前(?:这张)?工单|本[单张]|该工单|工单详情|流转记录).*");
        if (orderId != null && (individual || content.matches(".*(?:进度|到哪一步|什么状态|处理到哪).*"))) {
            return new Query(Kind.ORDER_DETAIL, orderId, null, null);
        }
        if (individual) {
            return new Query(Kind.ORDER_DETAIL, null, null, null);
        }
        if (content.contains("工单") || content.contains("报修记录") || content.contains("派给我")
                || content.contains("我接的单") || content.contains("待接单")) {
            return new Query(Kind.ORDERS, null, null, orderStatus(content, role));
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

    @Transactional(readOnly = true)
    public Table execute(Query query, int page) {
        String role = SecurityUtils.currentRoleCode();
        return switch (query.kind()) {
            case ORDERS -> orderTable(query, role, page);
            case ORDER_DETAIL -> orderDetail(query.orderId(), role, page);
            case TODOS -> {
                requireRole("WORKER");
                yield workOrderRows("我的待办", orders.pageForWorkerTodos(SecurityUtils.requireWorkerId(),
                        page(new OrderQueryRequestVO(), page)), role, page);
            }
            case PENDING_WORKERS -> pendingWorkers(page);
            case WORK_STATUS -> workStatus(page);
            case NOTIFICATIONS -> notificationTable(role, page);
            case ANNOUNCEMENTS -> announcementTable(role, page);
        };
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
                        item.getStatusText(), date(item.getCreateTime()))).toList(), result.getTotal(), page);
    }

    private Table orderDetail(Long id, String role, int page) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_ERROR, "请在工单详情页提问，或直接提供工单号");
        }
        OrderDetailResponseVO order = switch (role) {
            case "OWNER" -> orders.getDetailForOwner(id);
            case "ADMIN" -> orders.getDetailForAdmin(id);
            case "WORKER" -> orders.getAssignedDetailForWorker(id);
            default -> throw BizException.of(ResultCode.FORBIDDEN);
        };
        List<Row> rows = new ArrayList<>();
        String orderPath = path(role, "order/" + id);
        var logs = orderLogs.listByOrder(id);
        var lastUpdate = logs.isEmpty() ? null : logs.get(logs.size() - 1).getCreateTime();
        rows.add(row(orderPath, date(lastUpdate), "当前状态", order.getStatusText()));
        logs.forEach(item -> rows.add(row(null, date(item.getCreateTime()),
                item.getActionText(), item.getToStatusText())));
        int from = (int) Math.min(rows.size(), ((long) page - 1) * PAGE_SIZE);
        return table(order.getOrderNo() + " · " + order.getTitle(), List.of("时间", "事项", "状态"),
                rows.subList(from, Math.min(rows.size(), from + PAGE_SIZE)), rows.size(), page);
    }

    private Table pendingWorkers(int page) {
        requireRole("ADMIN");
        WorkerQueryRequestVO request = page(new WorkerQueryRequestVO(), page);
        request.setAuditStatus(0);
        var result = workers.page(request);
        return table("待审核师傅", List.of("工号", "师傅", "工种", "审核状态"), result.getRecords().stream()
                .map(item -> row("/admin/worker", item.getWorkerNo(), item.getRealName(), item.getSkillTags(),
                        item.getAuditStatusText())).toList(), result.getTotal(), page);
    }

    private Table workStatus(int page) {
        requireRole("WORKER");
        var worker = workers.requireCurrentWorker();
        WorkStatusEnum status = WorkStatusEnum.of(worker.getWorkStatus());
        var rows = page == 1 ? List.of(row("/worker/workbench", worker.getWorkerNo(),
                status == null ? null : status.getName())) : List.<Row>of();
        return table("当前工作状态", List.of("工号", "状态"), rows, 1, page);
    }

    private Table notificationTable(String role, int page) {
        var result = notifications.page(page(new PageRequestVO(), page));
        return table("我的通知", List.of("标题", "状态", "时间"), result.getRecords().stream()
                .map(item -> row(path(role, "notification"), item.getTitle(),
                        Integer.valueOf(1).equals(item.getIsRead()) ? "已读" : "未读", date(item.getCreateTime())))
                .toList(), result.getTotal(), page);
    }

    private Table announcementTable(String role, int page) {
        var result = announcements.pagePublishedForRole(page(new PageRequestVO(), page), role);
        return table("已发布公告", List.of("标题", "内容摘要", "发布时间"), result.getRecords().stream()
                .map(item -> row(path(role, "announcement"), item.getTitle(), summarize(item.getContent()),
                        date(item.getPublishTime()))).toList(), result.getTotal(), page);
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

    private static String orderStatus(String content, String role) {
        if (content.contains("派给我") || content.contains("已派单")) return "ASSIGNED";
        if (content.contains("已接单") || content.contains("我接的单")) return "ACCEPTED";
        if (content.contains("处理中") || content.contains("进行中")) return "GOING";
        if (content.contains("待确认") || content.contains("已提交完工")) return "SUBMITTED";
        if (content.contains("已完工")) return "COMPLETED";
        if (content.contains("待接单") && !"WORKER".equals(role)) return "PENDING";
        return null;
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

    private static Table table(String title, List<String> columns, List<Row> rows, long total, int page) {
        return new Table(title, columns, rows, total, page, (long) page * PAGE_SIZE < total);
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

    public enum Kind { ORDERS, ORDER_DETAIL, TODOS, PENDING_WORKERS, WORK_STATUS, NOTIFICATIONS, ANNOUNCEMENTS }

    public record Query(Kind kind, Long orderId, String keyword, String status) {
    }
}
