package jquant.service.api.option.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

@Schema(description = "Request payload for multi-engine vanilla option pricing and Greeks comparison")
public record VanillaOptionRequest(
        @Schema(description = "Option payoff type: CALL or PUT", example = "CALL")
        @NotNull String optionType,

        @Schema(description = "Exercise style: EUROPEAN or AMERICAN", example = "EUROPEAN")
        @NotNull String exerciseStyle,

        @Schema(description = "Valuation date (YYYY-MM-DD)", example = "2025-01-15")
        @NotNull LocalDate evaluationDate,

        @Schema(description = "Maturity / expiry date (YYYY-MM-DD)", example = "2026-01-15")
        @NotNull LocalDate maturityDate,

        @Schema(description = "Underlying spot price", example = "100.0")
        @Positive double spot,

        @Schema(description = "Option strike price", example = "100.0")
        @Positive double strike,

        @Schema(description = "Continuously compounded risk-free rate (decimal)", example = "0.045")
        double riskFreeRate,

        @Schema(description = "Continuously compounded dividend yield (decimal)", example = "0.015")
        @PositiveOrZero double dividendYield,

        @Schema(description = "Black-Scholes implied volatility (decimal)", example = "0.22")
        @Positive double volatility,

        @Schema(description = "Binomial tree time steps", example = "200")
        @Min(10) @Max(2000) Integer binomialSteps,

        @Schema(description = "FDM time grid points", example = "100")
        @Min(10) @Max(1000) Integer fdmTimeGrid,

        @Schema(description = "FDM spatial grid points", example = "100")
        @Min(10) @Max(1000) Integer fdmSpaceGrid
) {
}
