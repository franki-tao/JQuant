export interface HealthResponse {
  status: string;
  service: string;
  engine: string;
  javaVersion: string;
  timestamp: string;
  modules: string[];
}

export interface FixedRateBondRequest {
  evaluationDate: string;
  issueDate: string;
  maturityDate: string;
  faceAmount: number;
  couponRate: number;
  discountRate: number;
  settlementDays: number;
  redemption: number;
  frequency: string;
  dayCounter: string;
  calendar: string;
  compounding: string;
  businessDayConvention: string;
}

export interface QuoteBumpSummary {
  bumpBps: number;
  baseSettlementValue: number;
  upSettlementValue: number;
  downSettlementValue: number;
  centralPvChange: number;
  upDelta: number;
  downDelta: number;
}

export interface CashFlowRow {
  index: number;
  paymentDate: string;
  type: 'COUPON' | 'REDEMPTION' | string;
  amount: number;
  discountFactor: number;
  presentValue: number;
}

export interface PriceYieldPoint {
  yieldPct: number;
  cleanPrice: number;
  dirtyPrice: number;
}

export interface FixedRateBondResponse {
  evaluationDate: string;
  settlementDate: string;
  maturityDate: string;
  faceAmount: number;
  cleanPrice: number;
  dirtyPrice: number;
  accruedAmount: number;
  settlementValue: number;
  npv: number;
  yieldToMaturity: number;
  macaulayDuration: number;
  modifiedDuration: number;
  convexity: number;
  basisPointValue: number;
  yieldValueBasisPoint: number;
  zSpreadBps: number;
  quoteBump: QuoteBumpSummary;
  cashFlows: CashFlowRow[];
  priceYieldCurve: PriceYieldPoint[];
  elapsedMs: number;
}

export interface VanillaOptionRequest {
  optionType: 'CALL' | 'PUT';
  exerciseStyle: 'EUROPEAN' | 'AMERICAN';
  evaluationDate: string;
  maturityDate: string;
  spot: number;
  strike: number;
  riskFreeRate: number;
  dividendYield: number;
  volatility: number;
  binomialSteps: number;
  fdmTimeGrid: number;
  fdmSpaceGrid: number;
}

export interface EngineResult {
  engineName: string;
  category: string;
  npv: number;
  diffVsBenchmark: number | null;
  delta: number | null;
  gamma: number | null;
  vega: number | null;
  theta: number | null;
  rho: number | null;
  elapsedMs: number;
}

export interface SpotProfilePoint {
  spot: number;
  optionValue: number;
  intrinsicValue: number;
  delta: number | null;
  gamma: number | null;
}

export interface VanillaOptionResponse {
  optionType: string;
  exerciseStyle: string;
  evaluationDate: string;
  maturityDate: string;
  timeToMaturityYears: number;
  spot: number;
  strike: number;
  benchmarkNpv: number;
  benchmarkEngine: string;
  engines: EngineResult[];
  spotProfile: SpotProfilePoint[];
  totalElapsedMs: number;
}

export interface PresetItem<T> {
  id: string;
  name: string;
  description: string;
  request: T;
}
