package jquant.service.api.option;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jquant.service.api.option.dto.VanillaOptionRequest;
import jquant.service.api.option.dto.VanillaOptionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/options")
@Tag(name = "Equity & FX Options", description = "Multi-engine vanilla option pricing, Greeks, and spot payoff lab")
public class OptionController {

    private final OptionPricingService optionPricingService;

    public OptionController(OptionPricingService optionPricingService) {
        this.optionPricingService = optionPricingService;
    }

    @PostMapping("/vanilla/compare")
    @Operation(
            summary = "Compare vanilla option pricing engines",
            description = "Prices a European or American Call/Put across Analytic Black-Scholes, Binomial trees "
                    + "(CRR, Leisen-Reimer, Joshi), and Finite Differences (FDM), returning NPV, Greeks, "
                    + "benchmark differences, timings, and a spot payoff profile.")
    public VanillaOptionResponse compareVanilla(@Valid @RequestBody VanillaOptionRequest request) {
        return optionPricingService.compareVanillaEngines(request);
    }

    @GetMapping("/presets")
    @Operation(summary = "List curated vanilla option presets for quick demo exploration")
    public List<Map<String, Object>> presets() {
        return optionPricingService.presets();
    }
}
