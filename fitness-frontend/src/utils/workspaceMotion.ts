import type { ComputedRef, InjectionKey } from 'vue'

export const workspaceMotionKey: InjectionKey<ComputedRef<boolean>> = Symbol('workspace-motion')
