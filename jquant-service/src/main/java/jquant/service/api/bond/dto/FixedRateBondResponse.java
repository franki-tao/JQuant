package jquant.service.api.bond.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Full valuation, risk analytics, cash-flow schedule, and price-yield curve for a fixed-rate bond")
public record FixedRateBondResponse(
        LocalDate evaluationDate,
        LocalDate settlementDate,
        LocalDate maturityDate,
        double faceAmount,
        double cleanPrice,
        double dirtyPrice,
        double accruedAmount,
        double settlementValue,
        double npv,
        double yieldToMaturity,
        double macaulayDuration,
        double modifiedDuration,
        double convexity,
        double basisPointValue,
        double yieldValueBasisPoint,
        double zSpreadBps,
        QuoteBumpSummary quoteBump,
        List<CashFlowRow> cashFlows,
        List<PriceYieldPoint> priceYieldCurve,
        double elapsedMs
) {
    @Schema(description = "Central +1bp / -1bp quote bump-and-revalue result on settlementValue()")
    public record QuoteBumpSummary(
            double bumpBps,
            double baseSettlementValue,
            double upSettlementValue,
            double downSettlementValue,
            double centralPvChange,
            double upDelta,
            double downDelta
    ) {
    }

    @Schema(description = "Single cash-flow row in the bond schedule")
    public record CashFlowRow(
            int index,
            LocalDate paymentDate,
            String type,
            double amount,
            double discountFactor,
            double presentValue
    ) {
    }

    @Schema(description = "Clean and dirty price at a given flat yield level")
    public record PriceYieldPoint(
            double yieldPct,
            double cleanPrice,
            double dirtyPrice
    ) {
    }
}
