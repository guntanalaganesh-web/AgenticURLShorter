package com.schwab.assessment.scenario.artifact;

import java.util.List;

/**
 * REQUIREMENTS stage output: normalized functional and non-functional
 * requirements, plus a note of which requirement-text ambiguities (if any)
 * were resolved to produce them.
 */
public record RequirementsDocument(
        List<String> functionalRequirements,
        List<String> nonFunctionalRequirements,
        List<String> ambiguitiesResolved
) {
}
