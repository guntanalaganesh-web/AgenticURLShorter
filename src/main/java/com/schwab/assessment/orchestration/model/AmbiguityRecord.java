package com.schwab.assessment.orchestration.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a vague or underspecified requirement the engine encountered, the
 * assumption it made to proceed without blocking on human input, and how
 * confident it is in that assumption. Surfaced in full via
 * {@code GET /orchestration/decisions} so a reviewer can see exactly what
 * was inferred and why.
 *
 * @param id             unique identifier
 * @param runId          the pipeline run this ambiguity was surfaced in
 * @param stage          the stage during which the ambiguity was detected (typically REQUIREMENTS)
 * @param question       the ambiguity, phrased as a question
 * @param assumption     the assumption made to resolve it
 * @param confidence     how confident the engine is in that assumption
 * @param impactIfWrong  what breaks, or must be redone, if the assumption turns out wrong
 * @param timestamp      when the ambiguity was recorded
 */
public record AmbiguityRecord(
        UUID id,
        UUID runId,
        Stage stage,
        String question,
        String assumption,
        Confidence confidence,
        String impactIfWrong,
        Instant timestamp
) {

    public static AmbiguityRecord of(UUID runId, Stage stage, String question, String assumption,
                                      Confidence confidence, String impactIfWrong) {
        return new AmbiguityRecord(UUID.randomUUID(), runId, stage, question, assumption,
                confidence, impactIfWrong, Instant.now());
    }
}
