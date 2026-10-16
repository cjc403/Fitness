export class PageTaskEngine {
  constructor(options: any)
  options: any
  create(goal: string, config?: any): Promise<any>
  list(): any[]
  get(id: string): any
  pause(id: string): any
  resume(id: string): any
  stop(id: string): any
  confirm(id: string, confirmationId: string): Promise<any>
  input(id: string, message: string): any
  suspend(): void
  invalidate(): void
}
