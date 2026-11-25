package jquant.service.api.bond;

import jquant.CashFlow;
import jquant.Compounding;
import jquant.Handle;
import jquant.RelinkableHandle;
import jquant.Settings;
import jquant.cashflows.Duration;
import jquant.cashflows.Redemption;
import jquant.instruments.Bond;
import jquant.instruments.FixedRateBond;
import jquant.instruments.QuoteBumpRevalue;
import jquant.pricingengines.DiscountingBondEngine;
import jquant.pricingengines.bond.BondFunctions;
import jquant.quotes.SimpleQuote;
import jquant.service.api.bond.dto.FixedRateBondRequest;
import jquant.service.api.bond.dto.FixedRateBondResponse;
import jquant.service.common.QuantTypeMapper;
import jquant.service.execution.QuantExecutionGuard;
import jquant.termstructures.YieldTermStructure;
import jquant.termstructures.yield.FlatForward;
import jquant.time.BusinessDayConvention;
import jquant.time.Calendar;
import jquant.time.Date;
import jquant.time.DateGenerationRule;
import jquant.time.DayCounter;
import jquant.time.Frequency;
import jquant.time.Period;
import jquant.time.Schedule;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BondPricingService {

    private final QuantExecutionGuard executionGuard;

    public BondPricingService(QuantExecutionGuard executionGuard) {
        this.executionGuard = executionGuard;
    }

    public FixedRateBondResponse analyzeFixedRateBond(FixedRateBondRequest req) {
        if (!req.maturityDate().isAfter(req.issueDate())) {
            throw new IllegalArgumentException("maturityDate must be strictly after issueDate");
        }
        return executionGuard.execute(() -> computeFixedRateBond(req));
    }

    public List<Map<String, Object>> presets() {
        return List.of(
                Map.of(
                        "id", "ust-5y",
                        "name", "US Treasury 5Y (4.25%)",
                        "description", "Benchmark 5-year semiannual US government bond priced on a 4.50% flat curve",
                        "request", new FixedRateBondRequest(
                                LocalDate.of(2025, 1, 15),
                                LocalDate.of(2025, 1, 15),
                                LocalDate.of(2030, 1, 15),
                                1_000_000.0,
                                0.0425,
                                0.0450,
                                1,
                                100.0,
                                "SEMIANNUAL",
                                "ACT_ACT_ISMA",
                                "US_GOV",
                                "COMPOUNDED",
                                "MODIFIED_FOLLOWING"
                        )
                ),
                Map.of(
                        "id", "corp-10y",
                        "name", "IG Corporate 10Y (5.50%)",
                        "description", "10-year 30/360 semiannual corporate bond priced at a 5.15% discount rate",
                        "request", new FixedRateBondRequest(
                                LocalDate.of(2025, 3, 3),
                                LocalDate.of(2024, 9, 1),
                                LocalDate.of(2034, 9, 1),
                                5_000_000.0,
                                0.0550,
                                0.0515,
                                2,
                                100.0,
                                "SEMIANNUAL",
                                "THIRTY_360_USA",
                                "US_SETTLEMENT",
                                "COMPOUNDED",
                                "MODIFIED_FOLLOWING"
                        )
                ),
                Map.of(
                        "id", "bund-7y",
                        "name", "EUR Bund 7Y (2.60%)",
                        "description", "Annual TARGET-calendar European sovereign bond",
                        "request", new FixedRateBondRequest(
                                LocalDate.of(2025, 2, 10),
                                LocalDate.of(2025, 2, 10),
                                LocalDate.of(2032, 2, 10),
                                2_000_000.0,
                                0.0260,
                                0.0245,
                                2,
                                100.0,
                                "ANNUAL",
                                "ACT_ACT_ISMA",
                                "TARGET",
                                "COMPOUNDED",
                                "MODIFIED_FOLLOWING"
                        )
                )
        );
    }

    private FixedRateBondResponse computeFixedRateBond(FixedRateBondRequest req) {
        long startNanos = System.nanoTime();

        Date evalDate = QuantTypeMapper.toQuantDate(req.evaluationDate());
        Date issueDate = QuantTypeMapper.toQuantDate(req.issueDate());
        Date maturityDate = QuantTypeMapper.toQuantDate(req.maturityDate());

        Settings.instance.evaluationDate().equal(evalDate);

        Calendar calendar = QuantTypeMapper.toCalendar(req.calendar());
        DayCounter dayCounter = QuantTypeMapper.toDayCounter(req.dayCounter());
        Frequency frequency = QuantTypeMapper.toFrequency(req.frequency());
        Compounding compounding = QuantTypeMapper.toCompounding(req.compounding());
        BusinessDayConvention bdc = QuantTypeMapper.toBusinessDayConvention(req.businessDayConvention());

        Schedule schedule = new Schedule(
                issueDate,
                maturityDate,
                new Period(frequency),
                calendar,
                BusinessDayConvention.UNADJUSTED,
                BusinessDayConvention.UNADJUSTED,
                DateGenerationRule.BACKWARD,
                false,
                new Date(),
                new Date());

        FixedRateBond bond = new FixedRateBond(
                req.settlementDays(),
                req.faceAmount(),
                schedule,
                new double[]{req.couponRate()},
                dayCounter,
                bdc,
                req.redemption(),
                issueDate);

        SimpleQuote rateQuote = new SimpleQuote(req.discountRate());
        FlatForward discountCurve = new FlatForward(
                evalDate,
                new Handle<>(rateQuote, true),
                dayCounter,
                compounding,
                frequency);
        RelinkableHandle<YieldTermStructure> curveHandle = new RelinkableHandle<>(discountCurve, true);
        bond.setPricingEngine(new DiscountingBondEngine(curveHandle));

        Date settlementDate = bond.settlementDate();
        double cleanPrice = bond.cleanPrice();
        double dirtyPrice = bond.dirtyPrice();
        double accrued = bond.accruedAmount(settlementDate);
        double settlementValue = bond.settlementValue();
        double npv = bond.NPV();

        double ytm = BondFunctions.yield(
                bond,
                new Bond.Price(cleanPrice, Bond.Price.Type.CLEAN),
                dayCounter,
                compounding,
                frequency,
                settlementDate);
        double macaulayDuration = BondFunctions.duration(
                bond, ytm, dayCounter, compounding, frequency, Duration.Type.Macaulay, settlementDate);
        double modifiedDuration = BondFunctions.duration(
                bond, ytm, dayCounter, compounding, frequency, Duration.Type.Modified, settlementDate);
        double convexity = BondFunctions.convexity(
                bond, ytm, dayCounter, compounding, frequency, settlementDate);
        double bpv = BondFunctions.basisPointValue(
                bond, ytm, dayCounter, compounding, frequency, settlementDate);
        double yvbp = BondFunctions.yieldValueBasisPoint(
                bond, ytm, dayCounter, compounding, frequency, settlementDate);
        double zSpread = BondFunctions.zSpread(
                bond,
                new Bond.Price(cleanPrice, Bond.Price.Type.CLEAN),
                discountCurve,
                compounding,
                frequency,
                settlementDate);

        QuoteBumpRevalue.Result bumpResult =
                QuoteBumpRevalue.centralPvChangeForRateQuotes(bond, rateQuote);

        List<FixedRateBondResponse.CashFlowRow> cashFlowRows = new ArrayList<>();
        int idx = 1;
        for (CashFlow cf : bond.cashflows()) {
            Date cfDate = cf.date();
            if (cfDate.serialNumber() <= settlementDate.serialNumber()) {
                continue;
            }
            double df = discountCurve.discount(cfDate, false);
            double amt = cf.amount();
            String type = (cf instanceof Redemption) ? "REDEMPTION" : "COUPON";
            cashFlowRows.add(new FixedRateBondResponse.CashFlowRow(
                    idx++,
                    QuantTypeMapper.toLocalDate(cfDate),
                    type,
                    amt,
                    df,
                    amt * df));
        }

        List<FixedRateBondResponse.PriceYieldPoint> curvePoints = new ArrayList<>();
        double centerYield = ytm;
        for (int step = -8; step <= 8; step++) {
            double y = Math.max(0.0005, centerYield + step * 0.0025);
            double cp = BondFunctions.cleanPrice(bond, y, dayCounter, compounding, frequency, settlementDate);
            double dp = BondFunctions.dirtyPrice(bond, y, dayCounter, compounding, frequency, settlementDate);
            curvePoints.add(new FixedRateBondResponse.PriceYieldPoint(y * 100.0, cp, dp));
        }

        double elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0;

        return new FixedRateBondResponse(
                req.evaluationDate(),
                QuantTypeMapper.toLocalDate(settlementDate),
                QuantTypeMapper.toLocalDate(bond.maturityDate()),
                req.faceAmount(),
                cleanPrice,
                dirtyPrice,
                accrued,
                settlementValue,
                npv,
                ytm,
                macaulayDuration,
                modifiedDuration,
                convexity,
                bpv,
                yvbp,
                zSpread * 10_000.0,
                new FixedRateBondResponse.QuoteBumpSummary(
                        bumpResult.bump() * 10_000.0,
                        bumpResult.basePv(),
                        bumpResult.upPv(),
                        bumpResult.downPv(),
                        bumpResult.centralPvChange(),
                        bumpResult.upDelta(),
                        bumpResult.downDelta()),
                cashFlowRows,
                curvePoints,
                elapsedMs);
    }
}
