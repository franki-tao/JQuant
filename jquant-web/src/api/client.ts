import type {
  FixedRateBondRequest,
  FixedRateBondResponse,
  HealthResponse,
  PresetItem,
  VanillaOptionRequest,
  VanillaOptionResponse,
} from '../types/api';

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  });
  if (!res.ok) {
    let detail = `HTTP ${res.status}`;
    try {
      const err = await res.json();
      detail = err.message || err.error || detail;
    } catch {
      // ignore parse failure
    }
    throw new Error(detail);
  }
  return res.json() as Promise<T>;
}

export const api = {
  getHealth: () => requestJson<HealthResponse>('/api/v1/system/health'),

  getBondPresets: () =>
    requestJson<PresetItem<FixedRateBondRequest>[]>('/api/v1/bonds/presets'),

  analyzeFixedRateBond: (payload: FixedRateBondRequest) =>
    requestJson<FixedRateBondResponse>('/api/v1/bonds/fixed-rate/analyze', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getOptionPresets: () =>
    requestJson<PresetItem<VanillaOptionRequest>[]>('/api/v1/options/presets'),

  compareVanillaOptions: (payload: VanillaOptionRequest) =>
    requestJson<VanillaOptionResponse>('/api/v1/options/vanilla/compare', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
};
