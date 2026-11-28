package com.ycc.zhixiucloud.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 管家组合查询使用的明细、计数和分组投影。 */
@Mapper
public interface ConciergeQueryMapper {

    List<OrderRow> orderRows(@Param("scope") OrderScope scope, @Param("limit") int limit,
                             @Param("offset") long offset);

    List<OrderCount> orderCounts(@Param("scope") OrderScope scope);

    long workerGroupCount(@Param("scope") OrderScope scope);

    List<WorkerGroup> workerGroups(@Param("scope") OrderScope scope, @Param("limit") int limit,
                                   @Param("offset") long offset);

    List<LoadRow> loadRows(@Param("scope") LoadScope scope, @Param("limit") int limit,
                           @Param("offset") long offset);

    List<LoadCount> loadCounts(@Param("scope") LoadScope scope);

    record OrderScope(Long ownerId, Long workerId, boolean mine, Long orderId, String orderNo, String workerName,
                      List<String> statuses, String dateField, LocalDateTime from, LocalDateTime to) {}

    record LoadScope(int auditPassed, int disabled, boolean zeroLoad, boolean noPending, Integer workStatus) {}

    record OrderRow(Long id, String orderNo, String title, String status, LocalDateTime activityTime,
                    String workerName) {}

    record OrderCount(String status, long total) {}

    record WorkerGroup(String workerName, String workerNo, long total) {}

    record LoadRow(String workName, String workerNo, int workStatus, int currentLoad, long pending) {}

    record LoadCount(int workStatus, long total) {}
}
