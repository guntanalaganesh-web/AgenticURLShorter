package com.schwab.assessment.orchestration.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A single entry in the orchestration engine's decision lineage: a
 * decision made (or ambiguity resolved) during a stage, together with the
 * rationale and the alternatives that were considered. Persisted by
 * {@link com.schwab.assessment.orchestration.ContextStore} so the "why"
 * behind an engine action can be audited after the fact.
 *
 * @param id                      unique identifier of this decision entry
 * @param runId                   the pipeline run this decision was made in
 * @param stage                   the stage during which the decision was made
 * @param decision                short statement of what was decided
 * @param rationale               why this decision was made
 * @param alternativesConsidered  other options that were weighed and rejected
 * @param timestamp               when the decision was recorded
 */
public record DecisionRecord(
        UUID id,
        UUID runId,
        Stage stage,
        String decision,
        String rationale,
        List<String> alternativesConsidered,
        Instant timestamp
) {

    public static DecisionRecord of(UUID runId, Stage stage, String decision, String rationale,
                                     List<String> alternativesConsidered) {
        return new DecisionRecord(UUID.randomUUID(), runId, stage, decision, rationale,
                alternativesConsidered, Instant.now());
    }
}
