package com.schwab.assessment.api;

import com.schwab.assessment.orchestration.model.AmbiguityRecord;
import com.schwab.assessment.orchestration.model.DecisionRecord;

import java.util.List;

/**
 * Response body for {@code GET /orchestration/decisions}: the full
 * cross-run decision log, plus any ambiguity resolutions recorded for the
 * currently (or most recently) active run.
 */
public record DecisionLogResponse(List<DecisionRecord> decisions, List<AmbiguityRecord> ambiguities) {
}
