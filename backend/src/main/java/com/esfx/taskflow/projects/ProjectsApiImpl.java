package com.esfx.taskflow.projects;

import com.esfx.taskflow.common.ProjectsApi;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ProjectsApiImpl implements ProjectsApi {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final TaskService taskService;

    public ProjectsApiImpl(ProjectRepository projects, TaskRepository tasks, TaskService taskService) {
        this.projects = projects;
        this.tasks = tasks;
        this.taskService = taskService;
    }

    @Override
    public boolean canView(long projectId) {
        Long filter = ProjectService.visibilityFilter();
        return filter == null ? projects.findById(projectId).isPresent() : projects.isVisibleTo(projectId, filter);
    }

    @Override
    public List<Long> visibleProjectIds() {
        return projects.visibleIds(ProjectService.visibilityFilter());
    }

    @Override
    public Optional<TaskView> task(long taskId) {
        return tasks.findById(taskId).filter(t -> canView(t.projectId())).map(t -> taskService.toViews(List.of(t)).get(0));
    }

    @Override
    public Map<Long, TaskView> tasks(Collection<Long> taskIds) {
        Map<Long, TaskView> map = new LinkedHashMap<>();
        for (TaskView v : taskService.toViews(tasks.findByIds(taskIds))) map.put(v.id(), v);
        return map;
    }

    @Override
    public List<TaskView> overdueTasks(Collection<Long> projectIds, int limit) {
        return taskService.toViews(tasks.overdue(projectIds, LocalDate.now(com.esfx.taskflow.common.Dates.ZONE), limit));
    }

    @Override
    public List<TaskView> tasksDueBetween(Collection<Long> projectIds, LocalDate from, LocalDate to, Long assigneeId) {
        return taskService.toViews(tasks.dueBetween(projectIds, from, to, assigneeId));
    }

    @Override
    public List<TaskView> assignedTasksDueUntil(LocalDate until) {
        LocalDate from = LocalDate.now(com.esfx.taskflow.common.Dates.ZONE).minusDays(30);
        return taskService.toViews(tasks.assignedDueUntil(from, until));
    }
}
