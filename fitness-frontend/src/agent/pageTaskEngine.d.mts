export class PageTaskEngine {
  constructor(options: any)
  options: any
  create(goal: string, config?: any): Promise<any>
  list(): any[]
  get(id: string): any
  pause(id: string, reason?: 'USER' | 'PAGE_HIDDEN' | 'ASSISTANT_CLOSED' | 'OFFLINE'): any
  resume(id: string): any
  stop(id: string): any
  confirm(id: string, confirmationId: string): Promise<any>
  input(id: string, message: string): any
  suspend(reason?: 'USER' | 'PAGE_HIDDEN' | 'ASSISTANT_CLOSED' | 'OFFLINE'): void
  invalidate(): void
}
