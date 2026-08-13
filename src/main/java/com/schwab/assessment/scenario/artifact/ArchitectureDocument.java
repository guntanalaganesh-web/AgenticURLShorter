package com.schwab.assessment.scenario.artifact;

import java.util.List;
import java.util.Map;

/**
 * ARCHITECTURE stage output: the component list, references to ADRs that
 * back the design, and the chosen tech stack with a one-line rationale per
 * choice. Gated behind human design approval.
 */
public record ArchitectureDocument(
        List<String> components,
        List<String> adrReferences,
        Map<String, String> techStackRationale
) {
}
