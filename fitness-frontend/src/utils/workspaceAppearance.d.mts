export type MotionPreference = 'standard' | 'reduced'
export type MotionState = 'full' | 'reduced' | 'paused'
export function normalizeMotionPreference(value: unknown): MotionPreference
export function resolveMotionState(preference: unknown, systemReduced: boolean, hidden: boolean): MotionState
export function trendBarPercent(value: unknown, maximum: unknown): number
