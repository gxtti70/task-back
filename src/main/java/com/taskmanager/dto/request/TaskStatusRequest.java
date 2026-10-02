package com.taskmanager.dto.request;

import com.taskmanager.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskStatusRequest {
    @NotNull
    private TaskStatus status;
}
