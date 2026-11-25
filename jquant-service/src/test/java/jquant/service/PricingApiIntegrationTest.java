package jquant.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PricingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthAndOpenApiDocsAreAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("jquant-service"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("JQuant Pricing & Analytics API"));
    }

    @Test
    void fixedRateBondAnalyzeReturnsMetricsCashflowsAndQuoteBump() throws Exception {
        String body = """
                {
                  "evaluationDate": "2025-01-15",
                  "issueDate": "2025-01-15",
                  "maturityDate": "2030-01-15",
                  "faceAmount": 1000000,
                  "couponRate": 0.0425,
                  "discountRate": 0.045,
                  "settlementDays": 1,
                  "redemption": 100.0,
                  "frequency": "SEMIANNUAL",
                  "dayCounter": "ACT_ACT_ISMA",
                  "calendar": "US_GOV",
                  "compounding": "COMPOUNDED",
                  "businessDayConvention": "MODIFIED_FOLLOWING"
                }
                """;

        mockMvc.perform(post("/api/v1/bonds/fixed-rate/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleanPrice").isNumber())
                .andExpect(jsonPath("$.modifiedDuration").isNumber())
                .andExpect(jsonPath("$.quoteBump.centralPvChange").isNumber())
                .andExpect(jsonPath("$.cashFlows.length()").value(11))
                .andExpect(jsonPath("$.priceYieldCurve.length()").value(17));
    }

    @Test
    void vanillaOptionCompareReturnsAllEnginesAndSpotProfile() throws Exception {
        String body = """
                {
                  "optionType": "CALL",
                  "exerciseStyle": "EUROPEAN",
                  "evaluationDate": "2025-01-15",
                  "maturityDate": "2026-01-15",
                  "spot": 100.0,
                  "strike": 100.0,
                  "riskFreeRate": 0.045,
                  "dividendYield": 0.015,
                  "volatility": 0.22,
                  "binomialSteps": 200,
                  "fdmTimeGrid": 100,
                  "fdmSpaceGrid": 100
                }
                """;

        mockMvc.perform(post("/api/v1/options/vanilla/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchmarkEngine").value("Analytic Black-Scholes"))
                .andExpect(jsonPath("$.engines.length()").value(5))
                .andExpect(jsonPath("$.spotProfile.length()").value(21));
    }
}
