package com.esfx.taskflow.projects;

import java.time.Instant;
import java.time.LocalDate;

public record TaskRow(long id, long projectId, String projectCode, int taskNumber, String title, String status,
                      String priority, Long assigneeId, long reporterId, LocalDate dueDate, int progress,
                      int position, Instant createdAt, Instant updatedAt) {
    public String key() { return projectCode + "-" + taskNumber; }
}
