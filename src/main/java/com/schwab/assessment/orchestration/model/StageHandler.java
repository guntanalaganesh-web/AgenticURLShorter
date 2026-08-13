package com.schwab.assessment.orchestration.model;

/**
 * The actual unit of work behind a {@link Stage}. Scenario components
 * (greenfield, brownfield, ambiguous) implement one of these per stage and
 * register it on a {@link PipelineContext}; the orchestration engine itself
 * has no knowledge of what a stage "does" beyond invoking this contract.
 */
@FunctionalInterface
public interface StageHandler {

    /**
     * Executes one attempt of the stage's work.
     *
     * @param context the run's shared context, giving access to upstream artifacts
     * @param stage   the stage being executed (handlers may be reused across stages)
     * @param attempt the 1-based attempt number for this invocation
     * @return the outcome of this attempt
     * @throws Exception if the attempt fails outright (triggers a retry)
     */
    StageExecutionResult execute(PipelineContext context, Stage stage, int attempt) throws Exception;
}
