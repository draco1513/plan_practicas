package com.esfx.taskflow.timetracking;

import com.esfx.taskflow.common.UserSummary;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class TimeDtos {
    private TimeDtos() {}

    public record TimeEntryDto(long id, long taskId, String taskKey, long projectId, UserSummary user, LocalDate workDate,
                               double hours, String note) {}

    public record TimeEntryCreate(@NotNull Long taskId, @NotNull LocalDate workDate,
                                  @NotNull @DecimalMin(value = "0.25", message = "Mínimo 0.25 horas") @DecimalMax(value = "24", message = "Máximo 24 horas") Double hours,
                                  @Size(max = 300) String note) {}

    public record UserHours(UserSummary user, double hours) {}
    public record TaskHours(String taskKey, String title, double hours) {}
    public record TimeSummary(long projectId, double totalHours, List<UserHours> byUser, List<TaskHours> byTask) {}
}
