package jquant.service.api.bond;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jquant.service.api.bond.dto.FixedRateBondRequest;
import jquant.service.api.bond.dto.FixedRateBondResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bonds")
@Tag(name = "Fixed Income & Bonds", description = "Bond valuation, cash-flow analytics, and quote bump-and-revalue")
public class BondController {

    private final BondPricingService bondPricingService;

    public BondController(BondPricingService bondPricingService) {
        this.bondPricingService = bondPricingService;
    }

    @PostMapping("/fixed-rate/analyze")
    @Operation(
            summary = "Analyze a fixed-rate bond",
            description = "Computes clean/dirty price, accrued interest, YTM, Macaulay/Modified duration, convexity, "
                    + "BPV, Z-spread, central +1bp/-1bp quote bump-and-revalue, cash-flow schedule, and price-yield curve.")
    public FixedRateBondResponse analyzeFixedRateBond(@Valid @RequestBody FixedRateBondRequest request) {
        return bondPricingService.analyzeFixedRateBond(request);
    }

    @GetMapping("/presets")
    @Operation(summary = "List curated fixed-rate bond presets for quick demo exploration")
    public List<Map<String, Object>> presets() {
        return bondPricingService.presets();
    }
}
