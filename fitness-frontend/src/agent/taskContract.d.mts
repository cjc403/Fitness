export function roleSkills(role:string): {id:string;label:string}[]
export function createGoalContract(goal:string,role:string): any
export function verifyGoalContract(contract:any,evidence?:any[]): any
export function classifyFailure(error?:any): {strategy:string;retryable:boolean;label:string}
