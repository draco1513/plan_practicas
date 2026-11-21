package com.esfx.taskflow.projects;

import java.time.LocalDate;

public record ProjectRow(long id, String code, String name, long ownerId, String status, LocalDate startDate,
                         LocalDate endDate, int taskCount, int progress) {}
