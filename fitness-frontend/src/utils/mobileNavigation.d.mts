export interface MobileMenu { path: string; label: string; icon?: unknown; permission?: string; permissions?: string[]; roles?: string[] }
export function mobileNavigation<T extends MobileMenu>(items: T[], role: string | undefined, permissions: string[]): { primary: T[]; more: T[] }
