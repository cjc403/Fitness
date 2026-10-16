import type { IncomingMessage, ServerResponse } from 'node:http'
export interface LocalBridgeConfig { token: string; port: number; projectFingerprint: string; frontendOrigin?: string }
export function createLocalAgentBridge(options: { port: number; projectFingerprint: string; configPath?: string; readConfig?: () => LocalBridgeConfig }): (request: IncomingMessage, response: ServerResponse) => void
