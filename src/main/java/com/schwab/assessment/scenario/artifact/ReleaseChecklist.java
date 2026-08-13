package com.schwab.assessment.scenario.artifact;

import java.util.List;

/**
 * RELEASE_READINESS stage output: every go/no-go checklist item and
 * whether it passed. By the time this artifact is produced, the same
 * checks have already been enforced as hard gates by
 * {@link com.schwab.assessment.orchestration.PolicyGuardrail}; this
 * artifact is the human-readable record of that outcome.
 */
public record ReleaseChecklist(List<ChecklistItem> items) {

    public boolean allPassed() {
        return items.stream().allMatch(ChecklistItem::passed);
    }

    public record ChecklistItem(String name, boolean passed, String notes) {
    }
}
