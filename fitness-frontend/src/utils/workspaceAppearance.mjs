// Appearance helpers never participate in permissions or financial calculations.
export function normalizeMotionPreference(value) {
  return value === 'reduced' ? 'reduced' : 'standard'
}

export function resolveMotionState(preference, systemReduced, hidden) {
  if (hidden) return 'paused'
  return systemReduced || normalizeMotionPreference(preference) === 'reduced' ? 'reduced' : 'full'
}

export function trendBarPercent(value, maximum) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0 ||
      typeof maximum !== 'number' || !Number.isFinite(maximum) || maximum <= 0) return 0
  return Math.min(100, value / maximum * 100)
}
