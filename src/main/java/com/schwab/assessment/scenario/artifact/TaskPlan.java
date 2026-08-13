package com.schwab.assessment.scenario.artifact;

import com.schwab.assessment.orchestration.model.PolicyScannable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TASK_PLANNING stage output: an ordered list of implementation tasks with
 * estimated effort and dependencies. Implements {@link PolicyScannable} so
 * {@link com.schwab.assessment.orchestration.PolicyGuardrail} can scan task
 * descriptions for policy red flags before IMPLEMENTATION is allowed to start.
 */
public record TaskPlan(List<PlannedTask> tasks) implements PolicyScannable {

    @Override
    public String policyScanText() {
        return tasks.stream()
                .map(t -> t.id() + ": " + t.description())
                .collect(Collectors.joining("\n"));
    }

    /**
     * @param id            short task identifier, e.g. "T1"
     * @param description   what the task does
     * @param estimatedEffort human-readable effort estimate, e.g. "2h", "1d"
     * @param dependsOn     ids of tasks that must complete first
     */
    public record PlannedTask(String id, String description, String estimatedEffort, List<String> dependsOn) {
    }
}
