package jquant.service.api.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Service health and available pricing capabilities")
public class SystemController {

    public record HealthResponse(
            String status,
            String service,
            String engine,
            String javaVersion,
            Instant timestamp,
            List<String> modules) {
    }

    @GetMapping("/health")
    @Operation(summary = "Service health and capability metadata")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                "jquant-service",
                "JQuanlib (Java 17 QuantLib port)",
                System.getProperty("java.version"),
                Instant.now(),
                List.of("bonds", "options"));
    }
}
