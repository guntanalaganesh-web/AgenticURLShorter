package com.schwab.assessment.orchestration;

import com.schwab.assessment.orchestration.model.CoverageReporting;
import com.schwab.assessment.orchestration.model.MigrationAudit;
import com.schwab.assessment.orchestration.model.OpenApiPublished;
import com.schwab.assessment.orchestration.model.PipelineContext;
import com.schwab.assessment.orchestration.model.PolicyScannable;
import com.schwab.assessment.orchestration.model.PolicyViolationException;
import com.schwab.assessment.orchestration.model.Stage;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces per-stage governance policies before the orchestration engine is
 * allowed to execute a stage. Each policy failure raises a
 * {@link PolicyViolationException} carrying the specific rule that fired
 * and a concrete remediation hint, rather than a generic rejection.
 */
@Component
public class PolicyGuardrail {

    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern CARD_PATTERN = Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b");
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s\"']+)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "127.0.0.1", "0.0.0.0", "169.254.169.254", "::1");
    private static final List<String> BLOCKED_HOST_PREFIXES = List.of("10.", "172.16.", "192.168.");

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(password|passwd|api[_-]?key|secret|access[_-]?key)\\s*[:=]\\s*[\"']?[A-Za-z0-9/+_\\-]{6,}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern OWASP_RISK_PATTERN = Pattern.compile(
            "(?i)(eval\\(|Runtime\\.exec|\"\\s*\\+\\s*(user|input|request)|SELECT\\s+\\*\\s+FROM\\s+\"\\s*\\+)",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> DISALLOWED_LICENSES = Set.of("GPL-3.0", "AGPL-3.0", "SSPL-1.0");

    private static final double MIN_COVERAGE_PERCENTAGE = 80.0;

    /**
     * Validates that {@code stage} is allowed to execute given the current
     * pipeline context. Throws {@link PolicyViolationException} on the
     * first violated rule; returns normally if the stage passes every
     * applicable policy.
     */
    public void validate(Stage stage, PipelineContext context) {
        switch (stage) {
            case REQUIREMENTS -> validateRequirements(context);
            case IMPLEMENTATION -> validateImplementation(context);
            case RELEASE_READINESS -> validateReleaseReadiness(context);
            default -> {
                // no stage-specific policy for ARCHITECTURE, TASK_PLANNING, TESTING, DOCUMENTATION
            }
        }
    }

    private void validateRequirements(PipelineContext context) {
        String requirement = context.getRequirement() == null ? "" : context.getRequirement();

        if (SSN_PATTERN.matcher(requirement).find() || CARD_PATTERN.matcher(requirement).find()) {
            throw new PolicyViolationException(Stage.REQUIREMENTS, "PII-IN-REQUIREMENTS",
                    "Remove personally identifiable information (SSN or card-number-shaped sequences) "
                            + "from the requirement text; use anonymized placeholders instead.");
        }
        if (EMAIL_PATTERN.matcher(requirement).find()) {
            throw new PolicyViolationException(Stage.REQUIREMENTS, "PII-IN-REQUIREMENTS",
                    "Remove email addresses from the requirement text; reference users by role, not identity.");
        }

        Matcher urlMatcher = URL_PATTERN.matcher(requirement);
        while (urlMatcher.find()) {
            String candidate = urlMatcher.group(1);
            URI uri = parseUri(candidate);
            if (uri == null || uri.getHost() == null) {
                throw new PolicyViolationException(Stage.REQUIREMENTS, "MALFORMED-URL",
                        "Ensure any URL referenced in the requirement is a valid absolute http/https URI: "
                                + candidate);
            }
            if (isBlockedHost(uri.getHost())) {
                throw new PolicyViolationException(Stage.REQUIREMENTS, "SSRF-RISK-HOST",
                        "Requirement references an internal/loopback/link-local host (" + uri.getHost()
                                + "); restrict to public, externally resolvable hosts.");
            }
        }
    }

    private void validateImplementation(PipelineContext context) {
        String planText = scanTextOf(context.getArtifact(Stage.TASK_PLANNING));

        if (SECRET_PATTERN.matcher(planText).find() || AWS_KEY_PATTERN.matcher(planText).find()) {
            throw new PolicyViolationException(Stage.IMPLEMENTATION, "NO-HARDCODED-SECRETS",
                    "Task plan references a literal credential. Use environment variables or a secrets "
                            + "manager reference instead of embedding secret values in code or config.");
        }
        if (OWASP_RISK_PATTERN.matcher(planText).find()) {
            throw new PolicyViolationException(Stage.IMPLEMENTATION, "OWASP-TOP10-PATTERN",
                    "Task plan describes a pattern associated with injection/RCE risk (dynamic eval, "
                            + "shell exec, or string-concatenated SQL). Use parameterized queries and avoid "
                            + "dynamic code execution.");
        }

        @SuppressWarnings("unchecked")
        List<String> licenses = (List<String>) context.getAttribute("dependencyLicenses");
        if (licenses != null) {
            for (String license : licenses) {
                if (DISALLOWED_LICENSES.contains(license.toUpperCase(Locale.ROOT))) {
                    throw new PolicyViolationException(Stage.IMPLEMENTATION, "DEPENDENCY-LICENSE",
                            "Dependency declares disallowed license '" + license
                                    + "'. Replace it with a permissively-licensed (MIT/Apache-2.0/BSD) alternative.");
                }
            }
        }
    }

    private void validateReleaseReadiness(PipelineContext context) {
        Object testingArtifact = context.getArtifact(Stage.TESTING);
        if (testingArtifact instanceof CoverageReporting coverage) {
            if (coverage.coveragePercentage() < MIN_COVERAGE_PERCENTAGE) {
                throw new PolicyViolationException(Stage.RELEASE_READINESS, "COVERAGE-THRESHOLD",
                        String.format(Locale.ROOT,
                                "Test coverage is %.1f%%, below the required %.0f%% threshold. "
                                        + "Add tests for uncovered branches before requesting release approval.",
                                coverage.coveragePercentage(), MIN_COVERAGE_PERCENTAGE));
            }
        } else {
            throw new PolicyViolationException(Stage.RELEASE_READINESS, "COVERAGE-THRESHOLD",
                    "No test coverage report is available from the TESTING stage. "
                            + "TESTING must complete and publish a coverage-reporting artifact first.");
        }

        Object docArtifact = context.getArtifact(Stage.DOCUMENTATION);
        if (!(docArtifact instanceof OpenApiPublished openApi) || !openApi.hasOpenApiSpec()) {
            throw new PolicyViolationException(Stage.RELEASE_READINESS, "OPENAPI-SPEC-REQUIRED",
                    "No OpenAPI spec was published by the DOCUMENTATION stage. "
                            + "Generate and publish springdoc's /v3/api-docs output before release.");
        }

        Object implArtifact = context.getArtifact(Stage.IMPLEMENTATION);
        if (!(implArtifact instanceof MigrationAudit migrationAudit) || !migrationAudit.migrationScriptsReviewed()) {
            throw new PolicyViolationException(Stage.RELEASE_READINESS, "MIGRATION-REVIEW-REQUIRED",
                    "Flyway migration scripts introduced by IMPLEMENTATION have not been marked reviewed. "
                            + "Have a second engineer review new migrations before release.");
        }
    }

    private String scanTextOf(Object artifact) {
        if (artifact == null) {
            return "";
        }
        if (artifact instanceof PolicyScannable scannable) {
            return scannable.policyScanText();
        }
        return String.valueOf(artifact);
    }

    private boolean isBlockedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(normalized)) {
            return true;
        }
        return BLOCKED_HOST_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private URI parseUri(String candidate) {
        try {
            return new URI(candidate);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
