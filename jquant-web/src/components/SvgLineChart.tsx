import React, { useState } from 'react';

export interface ChartSeries {
  label: string;
  color: string;
  dashed?: boolean;
  values: (number | null)[];
}

interface SvgLineChartProps {
  title: string;
  subtitle?: string;
  xValues: number[];
  xFormatter?: (v: number) => string;
  yFormatter?: (v: number) => string;
  series: ChartSeries[];
  referenceX?: number;
  referenceLabel?: string;
  height?: number;
}

export const SvgLineChart: React.FC<SvgLineChartProps> = ({
  title,
  subtitle,
  xValues,
  xFormatter = (v) => v.toFixed(2),
  yFormatter = (v) => v.toFixed(2),
  series,
  referenceX,
  referenceLabel,
  height = 240,
}) => {
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  if (xValues.length < 2) {
    return null;
  }

  const width = 640;
  const pad = { top: 20, right: 20, bottom: 34, left: 58 };
  const plotW = width - pad.left - pad.right;
  const plotH = height - pad.top - pad.bottom;

  const allY: number[] = [];
  for (const s of series) {
    for (const v of s.values) {
      if (v !== null && Number.isFinite(v)) allY.push(v);
    }
  }
  if (allY.length === 0) return null;

  const minX = Math.min(...xValues);
  const maxX = Math.max(...xValues);
  let minY = Math.min(...allY);
  let maxY = Math.max(...allY);
  if (Math.abs(maxY - minY) < 1e-9) {
    minY -= 1;
    maxY += 1;
  } else {
    const margin = (maxY - minY) * 0.08;
    minY -= margin;
    maxY += margin;
  }

  const mapX = (x: number) => pad.left + ((x - minX) / (maxX - minX || 1)) * plotW;
  const mapY = (y: number) => pad.top + (1 - (y - minY) / (maxY - minY)) * plotH;

  const yTicks = Array.from({ length: 5 }, (_, i) => minY + ((maxY - minY) * i) / 4);
  const xTickIdx = [0, Math.floor((xValues.length - 1) / 2), xValues.length - 1];

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const relX = ((e.clientX - rect.left) / rect.width) * width;
    const clamped = Math.max(pad.left, Math.min(width - pad.right, relX));
    const ratio = (clamped - pad.left) / plotW;
    const idx = Math.round(ratio * (xValues.length - 1));
    setHoverIdx(Math.max(0, Math.min(xValues.length - 1, idx)));
  };

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <div className="chart-title">{title}</div>
          {subtitle && <div className="chart-subtitle">{subtitle}</div>}
        </div>
        <div className="chart-legend">
          {series.map((s) => (
            <span key={s.label} className="legend-item">
              <span
                className="legend-swatch"
                style={{
                  backgroundColor: s.dashed ? 'transparent' : s.color,
                  borderTop: s.dashed ? `2px dashed ${s.color}` : 'none',
                }}
              />
              {s.label}
            </span>
          ))}
        </div>
      </div>

      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="svg-chart"
        onMouseMove={handleMouseMove}
        onMouseLeave={() => setHoverIdx(null)}
      >
        {/* Horizontal gridlines */}
        {yTicks.map((tick, i) => {
          const y = mapY(tick);
          return (
            <g key={i}>
              <line
                x1={pad.left}
                x2={width - pad.right}
                y1={y}
                y2={y}
                stroke="rgba(148, 163, 184, 0.14)"
                strokeWidth="1"
              />
              <text
                x={pad.left - 8}
                y={y + 4}
                textAnchor="end"
                fill="#94a3b8"
                fontSize="10.5"
                fontFamily="var(--font-mono)"
              >
                {yFormatter(tick)}
              </text>
            </g>
          );
        })}

        {/* X axis ticks */}
        {xTickIdx.map((idx) => {
          const x = mapX(xValues[idx]);
          return (
            <text
              key={idx}
              x={x}
              y={height - 10}
              textAnchor="middle"
              fill="#94a3b8"
              fontSize="10.5"
              fontFamily="var(--font-mono)"
            >
              {xFormatter(xValues[idx])}
            </text>
          );
        })}

        {/* Reference vertical line */}
        {referenceX !== undefined && referenceX >= minX && referenceX <= maxX && (
          <g>
            <line
              x1={mapX(referenceX)}
              x2={mapX(referenceX)}
              y1={pad.top}
              y2={height - pad.bottom}
              stroke="rgba(245, 158, 11, 0.55)"
              strokeDasharray="4 4"
              strokeWidth="1.2"
            />
            {referenceLabel && (
              <text
                x={mapX(referenceX) + 5}
                y={pad.top + 12}
                fill="#fbbf24"
                fontSize="10"
                fontFamily="var(--font-mono)"
              >
                {referenceLabel}
              </text>
            )}
          </g>
        )}

        {/* Series paths */}
        {series.map((s) => {
          const dParts: string[] = [];
          s.values.forEach((v, i) => {
            if (v === null || !Number.isFinite(v)) return;
            const cmd = dParts.length === 0 ? 'M' : 'L';
            dParts.push(`${cmd} ${mapX(xValues[i]).toFixed(2)} ${mapY(v).toFixed(2)}`);
          });
          return (
            <path
              key={s.label}
              d={dParts.join(' ')}
              fill="none"
              stroke={s.color}
              strokeWidth={s.dashed ? 1.8 : 2.4}
              strokeDasharray={s.dashed ? '5 4' : undefined}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          );
        })}

        {/* Hover crosshair & dots */}
        {hoverIdx !== null && (
          <g>
            <line
              x1={mapX(xValues[hoverIdx])}
              x2={mapX(xValues[hoverIdx])}
              y1={pad.top}
              y2={height - pad.bottom}
              stroke="rgba(56, 189, 248, 0.45)"
              strokeWidth="1"
            />
            {series.map((s) => {
              const v = s.values[hoverIdx];
              if (v === null || !Number.isFinite(v)) return null;
              return (
                <circle
                  key={s.label}
                  cx={mapX(xValues[hoverIdx])}
                  cy={mapY(v)}
                  r="4"
                  fill={s.color}
                  stroke="#0b1120"
                  strokeWidth="1.5"
                />
              );
            })}
          </g>
        )}
      </svg>

      {hoverIdx !== null && (
        <div className="chart-hover-bar">
          <span className="mono accent">X: {xFormatter(xValues[hoverIdx])}</span>
          {series.map((s) => {
            const v = s.values[hoverIdx];
            return (
              <span key={s.label} className="mono">
                <span style={{ color: s.color }}>{s.label}:</span>{' '}
                {v !== null && Number.isFinite(v) ? yFormatter(v) : '—'}
              </span>
            );
          })}
        </div>
      )}
    </div>
  );
};
