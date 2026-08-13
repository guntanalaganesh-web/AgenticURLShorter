package com.schwab.assessment.api;

import com.schwab.assessment.orchestration.model.PipelineStatus;
import com.schwab.assessment.scenario.AmbiguousScenario;
import com.schwab.assessment.scenario.BrownFieldScenario;
import com.schwab.assessment.scenario.GreenFieldScenario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Triggers the three demonstration scenarios (Part 3 of this assessment)
 * that exercise the orchestration engine end-to-end. Each call blocks
 * until the pipeline reaches a terminal state -- COMPLETED or HALTED --
 * which, in manual-approval mode (orchestration.gates.auto-approve=false),
 * means the request blocks until a separate call to
 * {@code POST /orchestration/gates/{stageId}/approve} resolves the pending
 * gate; poll {@code GET /orchestration/status} from another client in the
 * meantime to watch it progress.
 */
@RestController
@Tag(name = "Scenarios", description = "Greenfield, brownfield, and ambiguous-requirement demonstration runs")
public class ScenarioController {

    private final GreenFieldScenario greenFieldScenario;
    private final BrownFieldScenario brownFieldScenario;
    private final AmbiguousScenario ambiguousScenario;

    public ScenarioController(GreenFieldScenario greenFieldScenario, BrownFieldScenario brownFieldScenario,
                               AmbiguousScenario ambiguousScenario) {
        this.greenFieldScenario = greenFieldScenario;
        this.brownFieldScenario = brownFieldScenario;
        this.ambiguousScenario = ambiguousScenario;
    }

    @Operation(summary = "Run a demonstration scenario",
            description = "type is one of: greenfield, brownfield, ambiguous.")
    @PostMapping("/scenarios/{type}/run")
    public ApiResponse<PipelineStatus> run(@PathVariable String type) {
        PipelineStatus status = switch (type.toLowerCase(Locale.ROOT)) {
            case "greenfield" -> greenFieldScenario.run();
            case "brownfield" -> brownFieldScenario.run();
            case "ambiguous" -> ambiguousScenario.run();
            default -> throw new IllegalArgumentException(
                    "Unknown scenario type: " + type + ". Valid types: greenfield, brownfield, ambiguous");
        };
        return ApiResponse.ok(status);
    }
}
