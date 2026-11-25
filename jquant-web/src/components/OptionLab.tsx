import React, { useEffect, useState } from 'react';
import { api } from '../api/client';
import type {
  PresetItem,
  VanillaOptionRequest,
  VanillaOptionResponse,
} from '../types/api';
import { SvgLineChart } from './SvgLineChart';

const DEFAULT_REQUEST: VanillaOptionRequest = {
  optionType: 'CALL',
  exerciseStyle: 'EUROPEAN',
  evaluationDate: '2025-01-15',
  maturityDate: '2026-01-15',
  spot: 100.0,
  strike: 100.0,
  riskFreeRate: 0.045,
  dividendYield: 0.015,
  volatility: 0.22,
  binomialSteps: 200,
  fdmTimeGrid: 100,
  fdmSpaceGrid: 100,
};

const fmtOpt = (v: number | null, digits = 5) =>
  v === null || !Number.isFinite(v) ? '—' : v.toFixed(digits);

export const OptionLab: React.FC = () => {
  const [presets, setPresets] = useState<PresetItem<VanillaOptionRequest>[]>([]);
  const [form, setForm] = useState<VanillaOptionRequest>(DEFAULT_REQUEST);
  const [result, setResult] = useState<VanillaOptionResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const runCompare = async (payload: VanillaOptionRequest) => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.compareVanillaOptions(payload);
      setResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Calculation failed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    api.getOptionPresets().then(setPresets).catch(() => {});
    runCompare(DEFAULT_REQUEST);
  }, []);

  const updateField = <K extends keyof VanillaOptionRequest>(
    key: K,
    value: VanillaOptionRequest[K]
  ) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  return (
    <div className="workbench-grid">
      <aside className="panel form-panel">
        <div className="panel-title-row">
          <h2>Vanilla Option Parameters</h2>
          <span className="pill">Multi-Engine Lab</span>
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
                  runCompare(p.request);
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
            runCompare(form);
          }}
        >
          <div className="toggle-pair">
            <div className="segmented">
              <button
                type="button"
                className={form.optionType === 'CALL' ? 'active' : ''}
                onClick={() => updateField('optionType', 'CALL')}
              >
                Call
              </button>
              <button
                type="button"
                className={form.optionType === 'PUT' ? 'active' : ''}
                onClick={() => updateField('optionType', 'PUT')}
              >
                Put
              </button>
            </div>
            <div className="segmented">
              <button
                type="button"
                className={form.exerciseStyle === 'EUROPEAN' ? 'active' : ''}
                onClick={() => updateField('exerciseStyle', 'EUROPEAN')}
              >
                European
              </button>
              <button
                type="button"
                className={form.exerciseStyle === 'AMERICAN' ? 'active' : ''}
                onClick={() => updateField('exerciseStyle', 'AMERICAN')}
              >
                American
              </button>
            </div>
          </div>

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
              <span>Maturity Date</span>
              <input
                type="date"
                value={form.maturityDate}
                onChange={(e) => updateField('maturityDate', e.target.value)}
              />
            </label>
            <label>
              <span>Spot (S)</span>
              <input
                type="number"
                step="1"
                value={form.spot}
                onChange={(e) => updateField('spot', Number(e.target.value))}
              />
            </label>
            <label>
              <span>Strike (K)</span>
              <input
                type="number"
                step="1"
                value={form.strike}
                onChange={(e) => updateField('strike', Number(e.target.value))}
              />
            </label>
          </div>

          <div className="slider-group">
            <div className="slider-header">
              <span>Implied Volatility (σ)</span>
              <span className="mono accent">{(form.volatility * 100).toFixed(1)}%</span>
            </div>
            <input
              type="range"
              min={0.05}
              max={0.8}
              step={0.005}
              value={form.volatility}
              onChange={(e) => updateField('volatility', Number(e.target.value))}
            />
          </div>

          <div className="slider-group">
            <div className="slider-header">
              <span>Risk-Free Rate (r)</span>
              <span className="mono emerald">{(form.riskFreeRate * 100).toFixed(2)}%</span>
            </div>
            <input
              type="range"
              min={-0.01}
              max={0.12}
              step={0.001}
              value={form.riskFreeRate}
              onChange={(e) => updateField('riskFreeRate', Number(e.target.value))}
            />
          </div>

          <div className="slider-group">
            <div className="slider-header">
              <span>Dividend Yield (q)</span>
              <span className="mono">{(form.dividendYield * 100).toFixed(2)}%</span>
            </div>
            <input
              type="range"
              min={0}
              max={0.08}
              step={0.001}
              value={form.dividendYield}
              onChange={(e) => updateField('dividendYield', Number(e.target.value))}
            />
          </div>

          <div className="field-grid-3">
            <label>
              <span>Tree Steps</span>
              <input
                type="number"
                min={20}
                max={1000}
                step={20}
                value={form.binomialSteps}
                onChange={(e) => updateField('binomialSteps', Number(e.target.value))}
              />
            </label>
            <label>
              <span>FDM T-Grid</span>
              <input
                type="number"
                min={20}
                max={500}
                step={10}
                value={form.fdmTimeGrid}
                onChange={(e) => updateField('fdmTimeGrid', Number(e.target.value))}
              />
            </label>
            <label>
              <span>FDM X-Grid</span>
              <input
                type="number"
                min={20}
                max={500}
                step={10}
                value={form.fdmSpaceGrid}
                onChange={(e) => updateField('fdmSpaceGrid', Number(e.target.value))}
              />
            </label>
          </div>

          <button type="submit" className="primary-btn" disabled={loading}>
            {loading ? 'Running Pricing Engines…' : 'Compare All Engines'}
          </button>
        </form>

        {error && <div className="error-banner">{error}</div>}
      </aside>

      <section className="results-column">
        {result && (
          <>
            <div className="kpi-grid">
              <div className="kpi-card highlight">
                <div className="kpi-label">Benchmark NPV</div>
                <div className="kpi-value mono">{result.benchmarkNpv.toFixed(6)}</div>
                <div className="kpi-sub">{result.benchmarkEngine}</div>
              </div>
              <div className="kpi-card">
                <div className="kpi-label">Contract</div>
                <div className="kpi-value mono">
                  {result.exerciseStyle} {result.optionType}
                </div>
                <div className="kpi-sub">
                  S={result.spot} · K={result.strike} · T={result.timeToMaturityYears.toFixed(3)}y
                </div>
              </div>
              <div className="kpi-card">
                <div className="kpi-label">Engines Evaluated</div>
                <div className="kpi-value mono emerald">{result.engines.length}</div>
                <div className="kpi-sub">
                  Total wall time: <span className="mono">{result.totalElapsedMs.toFixed(1)} ms</span>
                </div>
              </div>
              <div className="kpi-card">
                <div className="kpi-label">Max Lattice/FDM Error</div>
                <div className="kpi-value mono amber">
                  {Math.max(
                    ...result.engines.map((e) => Math.abs(e.diffVsBenchmark ?? 0))
                  ).toExponential(2)}
                </div>
                <div className="kpi-sub">vs {result.benchmarkEngine}</div>
              </div>
            </div>

            {/* Engine comparison table */}
            <div className="panel">
              <div className="panel-title-row">
                <h3>Multi-Engine Comparison Matrix</h3>
                <span className="muted">Simultaneous valuation via JQuanlib engines</span>
              </div>
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Engine</th>
                      <th>Method</th>
                      <th className="num">NPV</th>
                      <th className="num">Diff vs Bench</th>
                      <th className="num">Delta</th>
                      <th className="num">Gamma</th>
                      <th className="num">Vega</th>
                      <th className="num">Time (ms)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.engines.map((eng) => (
                      <tr key={eng.engineName}>
                        <td className="strong">{eng.engineName}</td>
                        <td>
                          <span
                            className={`badge ${
                              eng.category === 'Analytic'
                                ? 'badge-emerald'
                                : eng.category === 'Finite Differences'
                                ? 'badge-amber'
                                : 'badge-cyan'
                            }`}
                          >
                            {eng.category}
                          </span>
                        </td>
                        <td className="num mono">{eng.npv.toFixed(6)}</td>
                        <td className="num mono muted">
                          {eng.diffVsBenchmark === null
                            ? '—'
                            : eng.diffVsBenchmark === 0
                            ? '0.000000'
                            : `${eng.diffVsBenchmark >= 0 ? '+' : ''}${eng.diffVsBenchmark.toFixed(6)}`}
                        </td>
                        <td className="num mono">{fmtOpt(eng.delta, 4)}</td>
                        <td className="num mono">{fmtOpt(eng.gamma, 5)}</td>
                        <td className="num mono">{fmtOpt(eng.vega, 4)}</td>
                        <td className="num mono">{eng.elapsedMs.toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Spot Payoff & Delta Charts */}
            <div className="charts-2col">
              <SvgLineChart
                title="Option Value vs. Intrinsic Payoff"
                subtitle="Sweep across 65%–135% of strike"
                xValues={result.spotProfile.map((p) => p.spot)}
                xFormatter={(v) => v.toFixed(1)}
                yFormatter={(v) => v.toFixed(2)}
                referenceX={result.spot}
                referenceLabel={`Spot ${result.spot}`}
                series={[
                  {
                    label: 'Option Value',
                    color: '#38bdf8',
                    values: result.spotProfile.map((p) => p.optionValue),
                  },
                  {
                    label: 'Intrinsic',
                    color: '#94a3b8',
                    dashed: true,
                    values: result.spotProfile.map((p) => p.intrinsicValue),
                  },
                ]}
              />

              <SvgLineChart
                title="Delta Profile"
                subtitle="First-order spot sensitivity across underlying range"
                xValues={result.spotProfile.map((p) => p.spot)}
                xFormatter={(v) => v.toFixed(1)}
                yFormatter={(v) => v.toFixed(3)}
                referenceX={result.strike}
                referenceLabel={`Strike ${result.strike}`}
                series={[
                  {
                    label: 'Delta',
                    color: '#34d399',
                    values: result.spotProfile.map((p) => p.delta),
                  },
                ]}
              />
            </div>
          </>
        )}
      </section>
    </div>
  );
};
