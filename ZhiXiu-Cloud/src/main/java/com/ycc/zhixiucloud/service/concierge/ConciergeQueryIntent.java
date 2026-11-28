package com.ycc.zhixiucloud.service.concierge;

import com.ycc.zhixiucloud.common.enums.OrderStatusEnum;
import com.ycc.zhixiucloud.common.enums.WorkStatusEnum;
import com.ycc.zhixiucloud.service.concierge.ConciergeQueries.Kind;
import com.ycc.zhixiucloud.service.concierge.ConciergeQueries.Query;
import com.ycc.zhixiucloud.service.concierge.ConciergeToolModel.ModelFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 把模型选择转换为可执行、可保存的查询条件。权限身份和内部工单 ID 由服务器提供。
 *
 * <p>将相对日期在此处换算为固定区间，历史查询可以按原日期、当前权限重新执行。</p>
 */
@Component
@RequiredArgsConstructor
final class ConciergeQueryIntent {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern ORDER_NO = Pattern.compile("[A-Z0-9-]{6,50}");
    private final ConciergeToolModel model;

    Decision decide(String content, Long pageId, String role, Query previous) {
        // 模型只需知道当前是否有明确对象。具体页面 ID 和旧查询条件留在后端继承。
        String context = "角色：" + role;
        if (pageId != null) context += "；页面有明确工单";
        if (previous != null) {
            context += "；上一条查询类型：" + previous.kind();
            if (singleOrder(previous)) context += "；上一条明确查询单张工单";
            if (previous.kind() == Kind.WORKER_LOAD && previous.loadFilter() != null) {
                LoadFilter load = previous.loadFilter();
                context += "；上次负载模式：" + (load.noPending() ? LoadMode.NO_WORK
                        : load.zeroLoad() ? LoadMode.ZERO_LOAD : LoadMode.ALL);
            }
        }
        // 模型返回 name 和 arguments。名称决定走哪个固定分支，参数按该分支的 Java 类型读取。
        var call = model.choose(content, context);
        if (call.name() == null) throw new ModelFailure();
        return switch (call.name()) {
            case ConciergeToolModel.ORDERS -> orderList(model.arguments(call, OrderArgs.class), previous);
            case ConciergeToolModel.DETAIL -> detail(model.arguments(call, DetailArgs.class), content, pageId, previous);
            case ConciergeToolModel.LOAD -> load(model.arguments(call, LoadArgs.class), previous);
            case ConciergeToolModel.ROUTE -> new Decision(null,
                    Objects.requireNonNullElse(model.arguments(call, RouteArgs.class).route(), Route.UNSUPPORTED));
            default -> throw new ModelFailure();
        };
    }

    private static Decision orderList(OrderArgs args, Query previous) {
        OrderFilter old = null;
        String previousStatus = null;
        if (Boolean.TRUE.equals(args.inheritPrevious())) {
            // 列表筛选只继承列表条件。对单张工单的追问统一由详情工具处理。
            if (previous == null || previous.kind() != Kind.ORDER_REPORT && previous.kind() != Kind.ORDERS
                    || singleOrder(previous) || previous.keyword() != null) {
                return new Decision(null, Route.CLARIFY_QUERY);
            }
            old = previous.orderFilter();
            previousStatus = previous.status();
        }
        // 新参数优先。追问没有提供的项从上一轮继承，ALL 等显式值可以清掉原筛选。
        Milestone milestone = args.status() == null ? old == null ? null : old.milestone() : args.status().milestone;
        String status = args.status() == null ? previousStatus : args.status().rawStatus;
        Period range = args.period() == null ? old == null ? null : old.period() : period(args);
        if (args.period() == null && (args.fromDate() != null || args.toDate() != null)) throw new ModelFailure();
        String worker = args.workerName() == null ? old == null ? null : old.workerName() : args.workerName().strip();
        if (worker != null && worker.length() > 50) throw new ModelFailure();
        boolean grouped = args.groupByWorker() == null ? old != null && old.grouped() : args.groupByWorker();
        boolean mine = args.mine() == null ? old != null && old.mine() : args.mine();
        // 保存的是固定查询类型、时间区间和业务条件，翻页和重查无需重新理解原话。
        return new Decision(new Query(Kind.ORDER_REPORT, null, null, status,
                new OrderFilter(milestone, range, grouped, mine, worker == null || worker.isBlank() ? null : worker),
                null), null);
    }

    private static Decision detail(DetailArgs args, String content, Long pageId, Query previous) {
        String orderNo = args.orderNo() == null ? null : args.orderNo().strip().toUpperCase(Locale.ROOT);
        Long orderId = null;
        if (orderNo != null && !orderNo.isBlank()) {
            // 模型给出的订单号必须能在本次用户原话中核实。用边界避免截取另一单的号码。
            if (!ORDER_NO.matcher(orderNo).matches() || !Pattern.compile("(?<![A-Z0-9-])"
                    + Pattern.quote(orderNo) + "(?![A-Z0-9-])", Pattern.CASE_INSENSITIVE).matcher(content).find()) {
                return new Decision(null, Route.CLARIFY_ORDER);
            }
        } else if (Boolean.TRUE.equals(args.useContext())) {
            orderNo = null;
            // 有详情页时用本页工单。离开详情页时，只继承上一轮已经明确的单张工单。
            orderId = pageId;
            if (orderId == null && singleOrder(previous)) {
                orderId = previous.orderId();
                orderNo = previous.keyword();
            }
        }
        if (orderId == null && (orderNo == null || orderNo.isBlank())) {
            return new Decision(null, Route.CLARIFY_ORDER);
        }
        // 默认查进度。模型指定的字段须属于详情工具支持的枚举，重复字段只取一次。
        List<DetailField> fields = args.fields() == null || args.fields().isEmpty()
                ? List.of(DetailField.PROGRESS) : args.fields();
        if (fields.size() > DetailField.values().length || fields.stream().anyMatch(Objects::isNull)) {
            throw new ModelFailure();
        }
        return new Decision(new Query(Kind.ORDER_LOOKUP, orderId, orderNo, null, null, null,
                fields.stream().distinct().toList()), null);
    }

    private static Decision load(LoadArgs args, Query previous) {
        LoadFilter old = null;
        boolean hasPreviousLoad = previous != null && previous.kind() == Kind.WORKER_LOAD && previous.loadFilter() != null;
        // 仅传工作状态的追问，在已有负载查询时沿用该查询，避免在线筛选意外扩大工单范围。
        boolean inherits = Boolean.TRUE.equals(args.inheritPrevious())
                || args.inheritPrevious() == null && args.mode() == null && args.workStatus() != null && hasPreviousLoad;
        if (inherits) {
            if (!hasPreviousLoad) {
                return new Decision(null, Route.CLARIFY_QUERY);
            }
            old = previous.loadFilter();
        }
        // 追问只改变用户本次指定的字段。例如「只看在线」保留原来的在手数和待接数条件。
        LoadMode mode = inherits && args.mode() == LoadMode.ALL ? null : args.mode();
        boolean zeroLoad = mode == null ? old != null && old.zeroLoad() : mode != LoadMode.ALL;
        boolean noPending = mode == null ? old != null && old.noPending() : mode == LoadMode.NO_WORK;
        WorkStatusEnum status = args.workStatus() == null ? old == null ? null : old.workStatus() : args.workStatus().value;
        return new Decision(new Query(Kind.WORKER_LOAD, null, null, null, null, new LoadFilter(zeroLoad, noPending, status)),
                null);
    }

    private static boolean singleOrder(Query query) {
        return query != null && (query.kind() == Kind.ORDER_LOOKUP || query.kind() == Kind.ORDER_DETAIL
                || query.kind() == Kind.ORDER_REPORT)
                && (query.orderId() != null || query.keyword() != null);
    }

    private static Period period(OrderArgs args) {
        // 相对时间以业务时区换算。本周和本月的终点分别是下周一和下月第一天。
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate week = today.with(DayOfWeek.MONDAY);
        LocalDate month = today.withDayOfMonth(1);
        if (args.period() != PeriodChoice.CUSTOM && (args.fromDate() != null || args.toDate() != null)) {
            throw new ModelFailure();
        }
        return switch (args.period()) {
            case ALL -> null;
            case TODAY -> new Period(today, today.plusDays(1));
            case YESTERDAY -> new Period(today.minusDays(1), today);
            case THIS_WEEK -> new Period(week, week.plusWeeks(1));
            case LAST_WEEK -> new Period(week.minusWeeks(1), week);
            case THIS_MONTH -> new Period(month, month.plusMonths(1));
            case LAST_MONTH -> new Period(month.minusMonths(1), month);
            case LAST_7_DAYS -> new Period(today.minusDays(6), today.plusDays(1));
            case CUSTOM -> customPeriod(args.fromDate(), args.toDate());
        };
    }

    private static Period customPeriod(String start, String end) {
        if (start == null || end == null) throw new ModelFailure();
        try {
            LocalDate from = LocalDate.parse(start);
            LocalDate through = LocalDate.parse(end);
            if (from.getYear() < 1000 || through.getYear() > 9998 || through.isBefore(from)) throw new ModelFailure();
            // 用户给出的止日包含当天，查询条件统一使用左闭右开的日期区间。
            return new Period(from, through.plusDays(1));
        } catch (DateTimeException e) {
            throw new ModelFailure();
        }
    }

    enum Route { EXISTING, KNOWLEDGE, UNSUPPORTED, CLARIFY_ORDER, CLARIFY_QUERY }

    enum DetailField { ACTUAL_WORKER, EVALUATION, PROGRESS }

    enum PeriodChoice { ALL, TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, LAST_7_DAYS, CUSTOM }

    enum LoadMode { ALL, ZERO_LOAD, NO_WORK }

    enum WorkStatus {
        ALL(null), ONLINE(WorkStatusEnum.ONLINE), OFFLINE(WorkStatusEnum.OFFLINE), BUSY(WorkStatusEnum.BUSY);

        final WorkStatusEnum value;

        WorkStatus(WorkStatusEnum value) {
            this.value = value;
        }
    }

    enum Status {
        ALL(null, null), COMPLETED(Milestone.COMPLETED, null), WAITING_EVALUATION(Milestone.WAITING_EVALUATION, null),
        SUBMITTED(Milestone.SUBMITTED, null), CLOSED(Milestone.CLOSED, null),
        PENDING(null, "PENDING"), ASSIGNED(null, "ASSIGNED"), ACCEPTED(null, "ACCEPTED"), GOING(null, "GOING"),
        PAUSED(null, "PAUSED"), TRANSFER_PENDING(null, "TRANSFER_PENDING"), CANCELED(null, "CANCELED");

        final Milestone milestone;
        final String rawStatus;

        Status(Milestone milestone, String rawStatus) {
            this.milestone = milestone;
            this.rawStatus = rawStatus;
        }
    }

    enum Milestone {
        COMPLETED("已完成", "COMPLETED", "确认完工时间", OrderStatusEnum.COMPLETED, OrderStatusEnum.CLOSED),
        WAITING_EVALUATION("待评价", "COMPLETED", "确认完工时间", OrderStatusEnum.COMPLETED),
        SUBMITTED("已提交待确认", "SUBMITTED", "提交完工时间", OrderStatusEnum.SUBMITTED),
        CLOSED("已关闭", "CLOSED", "关单时间", OrderStatusEnum.CLOSED);

        final String label;
        final String dateField;
        final String dateLabel;
        final List<String> statuses;

        Milestone(String label, String dateField, String dateLabel, OrderStatusEnum... statuses) {
            this.label = label;
            this.dateField = dateField;
            this.dateLabel = dateLabel;
            this.statuses = List.of(statuses).stream().map(OrderStatusEnum::getCode).toList();
        }
    }

    record Decision(Query query, Route route) {}

    record OrderArgs(Status status, PeriodChoice period, String fromDate, String toDate, String workerName,
                     Boolean groupByWorker, Boolean mine, Boolean inheritPrevious) {}

    record DetailArgs(String orderNo, Boolean useContext, List<DetailField> fields) {}

    record LoadArgs(LoadMode mode, WorkStatus workStatus, Boolean inheritPrevious) {}

    record RouteArgs(Route route) {}

    record OrderFilter(Milestone milestone, Period period, boolean grouped, boolean mine, String workerName) {}

    record LoadFilter(boolean zeroLoad, boolean noPending, WorkStatusEnum workStatus) {}

    record Period(LocalDate from, LocalDate to) {}
}
