package jquant.service.api.bond.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

@Schema(description = "Request payload for fixed-rate bond valuation, risk analytics, and quote bump-revalue")
public record FixedRateBondRequest(
        @Schema(description = "Valuation date (YYYY-MM-DD)", example = "2025-01-15")
        @NotNull LocalDate evaluationDate,

        @Schema(description = "Bond issue / accrual start date", example = "2025-01-15")
        @NotNull LocalDate issueDate,

        @Schema(description = "Bond maturity date", example = "2030-01-15")
        @NotNull LocalDate maturityDate,

        @Schema(description = "Face amount (notional)", example = "1000000")
        @Positive double faceAmount,

        @Schema(description = "Annual coupon rate in decimal (e.g. 0.0425 = 4.25%)", example = "0.0425")
        @PositiveOrZero double couponRate,

        @Schema(description = "Flat discount rate in decimal (e.g. 0.045 = 4.50%)", example = "0.045")
        double discountRate,

        @Schema(description = "Settlement days (T+n)", example = "2")
        @PositiveOrZero int settlementDays,

        @Schema(description = "Redemption price per 100 face value", example = "100.0")
        @Positive double redemption,

        @Schema(description = "Coupon frequency: ANNUAL, SEMIANNUAL, QUARTERLY, MONTHLY", example = "SEMIANNUAL")
        String frequency,

        @Schema(description = "Accrual day counter: ACT_ACT_ISMA, ACT_ACT_ISDA, ACT_365F, ACT_360, THIRTY_360_USA", example = "ACT_ACT_ISMA")
        String dayCounter,

        @Schema(description = "Calendar: US_GOV, US_SETTLEMENT, TARGET, UK, NULL", example = "US_GOV")
        String calendar,

        @Schema(description = "Discount curve compounding: COMPOUNDED, CONTINUOUS, SIMPLE", example = "COMPOUNDED")
        String compounding,

        @Schema(description = "Payment business day convention: MODIFIED_FOLLOWING, FOLLOWING, PRECEDING, UNADJUSTED", example = "MODIFIED_FOLLOWING")
        String businessDayConvention
) {
}
