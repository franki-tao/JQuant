package jquant.service.api.option.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Multi-engine vanilla option comparison result and spot profile")
public record VanillaOptionResponse(
        String optionType,
        String exerciseStyle,
        LocalDate evaluationDate,
        LocalDate maturityDate,
        double timeToMaturityYears,
        double spot,
        double strike,
        double benchmarkNpv,
        String benchmarkEngine,
        List<EngineResult> engines,
        List<SpotProfilePoint> spotProfile,
        double totalElapsedMs
) {
    @Schema(description = "Single pricing engine output and benchmark error")
    public record EngineResult(
            String engineName,
            String category,
            double npv,
            Double diffVsBenchmark,
            Double delta,
            Double gamma,
            Double vega,
            Double theta,
            Double rho,
            double elapsedMs
    ) {
    }

    @Schema(description = "Option value, intrinsic payoff, and delta across a range of spot prices")
    public record SpotProfilePoint(
            double spot,
            double optionValue,
            double intrinsicValue,
            Double delta,
            Double gamma
    ) {
    }
}
