import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type {
  FixedRateBondRequest,
  FixedRateBondResponse,
  PresetItem,
} from '../types/api';
import { SvgLineChart } from './SvgLineChart';

const DEFAULT_REQUEST: FixedRateBondRequest = {
  evaluationDate: '2025-01-15',
  issueDate: '2025-01-15',
  maturityDate: '2030-01-15',
  faceAmount: 1_000_000,
  couponRate: 0.0425,
  discountRate: 0.045,
  settlementDays: 1,
  redemption: 100.0,
  frequency: 'SEMIANNUAL',
  dayCounter: 'ACT_ACT_ISMA',
  calendar: 'US_GOV',
  compounding: 'COMPOUNDED',
  businessDayConvention: 'MODIFIED_FOLLOWING',
};

const fmtNum = (n: number, digits = 4) =>
  n.toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });

const fmtMoney = (n: number) =>
  n.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

export const BondWorkbench: React.FC = () => {
  const [presets, setPresets] = useState<PresetItem<FixedRateBondRequest>[]>([]);
  const [form, setForm] = useState<FixedRateBondRequest>(DEFAULT_REQUEST);
  const [result, setResult] = useState<FixedRateBondResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runAnalysis = async (payload: FixedRateBondRequest) => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.analyzeFixedRateBond(payload);
      setResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Calculation failed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    api.getBondPresets().then(setPresets).catch(() => {});
    runAnalysis(DEFAULT_REQUEST);
  }, []);

  const updateField = <K extends keyof FixedRateBondRequest>(
    key: K,
    value: FixedRateBondRequest[K]
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  return (
    <div className="workbench-grid">
      {/* Left parameter panel */}
      <aside className="panel form-panel">
        <div className="panel-title-row">
          <h2>Fixed-Rate Bond Parameters</h2>
          <span className="pill">DiscountingBondEngine</span>
        </div>

        {presets.length > 0 && (
          <div className="preset-row">
            {presets.map((p) => (
              <button
                key={p.id}
                type="button"
                className="preset-chip"
                title={p.description}
                onClick={() => {
                  setForm(p.request);
                  runAnalysis(p.request);
                }}
              >
                {p.name}
              </button>
            ))}
          </div>
        )}

        <form
          className="param-form"
          onSubmit={(e) => {
            e.preventDefault();
            runAnalysis(form);
          }}
        >
          <div className="field-grid-2">
            <label>
              <span>Evaluation Date</span>
              <input
                type="date"
                value={form.evaluationDate}
                onChange={(e) => updateField('evaluationDate', e.target.value)}
              />
            </label>
            <label>
              <span>Settlement Days (T+n)</span>
              <input
                type="number"
                min={0}
                max={10}
                value={form.settlementDays}
                onChange={(e) => updateField('settlementDays', Number(e.target.value))}
              />
            </label>
            <label>
              <span>Issue Date</span>
              <input
                type="date"
                value={form.issueDate}
                onChange={(e) => updateField('issueDate', e.target.value)}
              />
            </label>
            <label>
              <span>Maturity Date</span>
              <input
                type="date"
                value={form.maturityDate}
                onChange={(e) => updateField('maturityDate', e.target.value)}
              />
            </label>
          </div>

          <div className="field-grid-2">
            <label>
              <span>Face Amount</span>
              <input
                type="number"
                step="10000"
                value={form.faceAmount}
                onChange={(e) => updateField('faceAmount', Number(e.target.value))}
              />
            </label>
            <label>
              <span>Redemption (per 100)</span>
              <input
                type="number"
                step="0.5"
                value={form.redemption}
                onChange={(e) => updateField('redemption', Number(e.target.value))}
              />
            </label>
          </div>

          <div className="slider-group">
            <div className="slider-header">
              <span>Coupon Rate</span>
              <span className="mono accent">{(form.couponRate * 100).toFixed(2)}%</span>
            </div>
            <input
              type="range"
              min={0}
              max={0.12}
              step={0.0005}
              value={form.couponRate}
              onChange={(e) => updateField('couponRate', Number(e.target.value))}
            />
          </div>

          <div className="slider-group">
            <div className="slider-header">
              <span>Flat Discount Rate</span>
              <span className="mono emerald">{(form.discountRate * 100).toFixed(2)}%</span>
            </div>
            <input
              type="range"
              min={0.001}
              max={0.12}
              step={0.0005}
              value={form.discountRate}
              onChange={(e) => updateField('discountRate', Number(e.target.value))}
            />
          </div>

          <div className="field-grid-2">
            <label>
              <span>Frequency</span>
              <select
                value={form.frequency}
                onChange={(e) => updateField('frequency', e.target.value)}
              >
                <option value="ANNUAL">Annual</option>
                <option value="SEMIANNUAL">Semiannual</option>
                <option value="QUARTERLY">Quarterly</option>
                <option value="MONTHLY">Monthly</option>
              </select>
            </label>
            <label>
              <span>Day Counter</span>
              <select
                value={form.dayCounter}
                onChange={(e) => updateField('dayCounter', e.target.value)}
              >
                <option value="ACT_ACT_ISMA">Act/Act (ISMA)</option>
                <option value="ACT_ACT_ISDA">Act/Act (ISDA)</option>
                <option value="ACT_365F">Act/365 Fixed</option>
                <option value="ACT_360">Act/360</option>
                <option value="THIRTY_360_USA">30/360 (USA)</option>
              </select>
            </label>
            <label>
              <span>Calendar</span>
              <select
                value={form.calendar}
                onChange={(e) => updateField('calendar', e.target.value)}
              >
                <option value="US_GOV">US Government Bond</option>
                <option value="US_SETTLEMENT">US Settlement</option>
                <option value="TARGET">TARGET (EUR)</option>
                <option value="UK">United Kingdom</option>
                <option value="NULL">Null Calendar</option>
              </select>
            </label>
            <label>
              <span>Compounding</span>
              <select
                value={form.compounding}
                onChange={(e) => updateField('compounding', e.target.value)}
              >
                <option value="COMPOUNDED">Compounded</option>
                <option value="CONTINUOUS">Continuous</option>
                <option value="SIMPLE">Simple</option>
              </select>
            </label>
          </div>

          <button type="submit" className="primary-btn" disabled={loading}>
            {loading ? 'Pricing with JQuanlib…' : 'Price & Analyze Bond'}
          </button>
        </form>

        {error && <div className="error-banner">{error}</div>}
      </aside>

      {/* Right analytics & charts panel */}
      <section className="results-column">
        {result && (
          <>
            <div className="kpi-grid">
              <div className="kpi-card highlight">
                <div className="kpi-label">Clean Price (per 100)</div>
                <div className="kpi-value mono">{fmtNum(result.cleanPrice, 4)}</div>
                <div className="kpi-sub">
                  Dirty: <span className="mono">{fmtNum(result.dirtyPrice, 4)}</span> · Accrued:{' '}
                  <span className="mono">{fmtNum(result.accruedAmount, 4)}</span>
                </div>
              </div>
              <div className="kpi-card">
                <div className="kpi-label">Yield to Maturity</div>
                <div className="kpi-value mono emerald">
                  {(result.yieldToMaturity * 100).toFixed(4)}%
                </div>
                <div className="kpi-sub">
                  Settle: <span className="mono">{result.settlementDate}</span> ·{' '}
                  <span className="mono">{result.elapsedMs.toFixed(1)} ms</span>
                </div>
              </div>
              <div className="kpi-card">
                <div className="kpi-label">Modified Duration</div>
                <div className="kpi-value mono">{fmtNum(result.modifiedDuration, 4)} y</div>
                <div className="kpi-sub">
                  Macaulay: <span className="mono">{fmtNum(result.macaulayDuration, 4)} y</span> ·
                  Convexity: <span className="mono">{fmtNum(result.convexity, 2)}</span>
                </div>
              </div>
              <div className="kpi-card">
                <div className="kpi-label">Settlement Value</div>
                <div className="kpi-value mono">{fmtMoney(result.settlementValue)}</div>
                <div className="kpi-sub">
                  Yield BPV: <span className="mono">{fmtMoney(result.basisPointValue)}</span>
                </div>
              </div>
            </div>

            {/* Quote Bump-and-Revalue Strip */}
            <div className="panel bump-strip">
              <div className="panel-title-row">
                <h3>Quote Bump-and-Revalue (±{result.quoteBump.bumpBps.toFixed(1)} bp Central Diff)</h3>
                <span className="pill subtle">QuoteBumpRevalue.centralPvChangeForRateQuotes</span>
              </div>
              <div className="bump-metrics">
                <div>
                  <span className="metric-k">Central PV Change (+1bp)</span>
                  <span className="metric-v mono amber">
                    {fmtMoney(result.quoteBump.centralPvChange)}
                  </span>
                </div>
                <div>
                  <span className="metric-k">Up Delta (+1bp)</span>
                  <span className="metric-v mono">{fmtMoney(result.quoteBump.upDelta)}</span>
                </div>
                <div>
                  <span className="metric-k">Down Delta (-1bp)</span>
                  <span className="metric-v mono">{fmtMoney(result.quoteBump.downDelta)}</span>
                </div>
                <div>
                  <span className="metric-k">Z-Spread</span>
                  <span className="metric-v mono">{result.zSpreadBps.toFixed(3)} bp</span>
                </div>
              </div>
            </div>

            {/* Price-Yield Curve */}
            <SvgLineChart
              title="Price–Yield Convexity Curve"
              subtitle="Clean and dirty price per 100 face across ±200 bp around current YTM"
              xValues={result.priceYieldCurve.map((p) => p.yieldPct)}
              xFormatter={(v) => `${v.toFixed(2)}%`}
              yFormatter={(v) => v.toFixed(2)}
              referenceX={result.yieldToMaturity * 100}
              referenceLabel={`YTM ${(result.yieldToMaturity * 100).toFixed(2)}%`}
              series={[
                {
                  label: 'Clean Price',
                  color: '#38bdf8',
                  values: result.priceYieldCurve.map((p) => p.cleanPrice),
                },
                {
                  label: 'Dirty Price',
                  color: '#34d399',
                  dashed: true,
                  values: result.priceYieldCurve.map((p) => p.dirtyPrice),
                },
              ]}
            />

            {/* Cash-flow schedule */}
            <div className="panel">
              <div className="panel-title-row">
                <h3>Cash-Flow Schedule ({result.cashFlows.length} flows)</h3>
                <span className="muted">Excluding flows on or before settlement date</span>
              </div>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>#</th>
                      <th>Payment Date</th>
                      <th>Type</th>
                      <th className="num">Amount</th>
                      <th className="num">Discount Factor</th>
                      <th className="num">Present Value</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.cashFlows.map((cf) => (
                      <tr key={`${cf.index}-${cf.type}`}>
                        <td className="mono muted">{cf.index}</td>
                        <td className="mono">{cf.paymentDate}</td>
                        <td>
                          <span
                            className={`badge ${
                              cf.type === 'REDEMPTION' ? 'badge-amber' : 'badge-cyan'
                            }`}
                          >
                            {cf.type}
                          </span>
                        </td>
                        <td className="num mono">{fmtMoney(cf.amount)}</td>
                        <td className="num mono">{cf.discountFactor.toFixed(6)}</td>
                        <td className="num mono">{fmtMoney(cf.presentValue)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  );
};
