<template>
  <section class="pwa-install" aria-label="安装与网络状态"><strong>{{ online ? 'FitChain 手机工作台' : '当前离线' }}</strong><p>{{ installText }}</p><button v-if="installEvent && secure && !standalone" type="button" @click="install">安装到桌面</button><small>业务数据需要联网读取；离线不会自动提交操作。</small></section>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
interface InstallEvent extends Event { prompt(): Promise<void>; userChoice: Promise<{ outcome: string }> }
const installEvent = ref<InstallEvent | null>(null), online = ref(navigator.onLine !== false), secure = window.isSecureContext
const standalone = ref(window.matchMedia('(display-mode: standalone)').matches || !!(navigator as Navigator & { standalone?: boolean }).standalone)
const installComplete = ref(false), installError = ref('')
const installText = computed(() => !online.value ? '请连接网络后继续，当前不能提交业务操作。' : standalone.value ? '已在独立应用窗口中打开。' : installComplete.value ? '已添加到桌面，可从桌面图标打开。' : installError.value || (!secure ? '当前为非安全连接。浏览器安装与离线壳需要 HTTPS（本机 localhost 除外）；局域网 HTTP 可直接浏览。' : installEvent.value ? '当前浏览器支持安装，可将工作台添加到桌面。' : /iphone|ipad|ipod/i.test(navigator.userAgent) ? '在 Safari 的分享菜单中选择「添加到主屏幕」。其他浏览器请查看其菜单。' : '可在浏览器菜单查看「安装应用」或「添加到主屏幕」。是否可用由浏览器决定。'))
const capture = (event: Event) => { event.preventDefault(); installEvent.value = event as InstallEvent }
const sync = () => { online.value = navigator.onLine !== false }
const installed = () => { installComplete.value = true; installEvent.value = null }
const install = async () => { const event = installEvent.value; if (!event || !secure) return; installEvent.value = null; try { await event.prompt(); if ((await event.userChoice).outcome === 'accepted') installComplete.value = true } catch { installError.value = '本次安装未完成，可在浏览器菜单中重试。' } }
onMounted(() => { window.addEventListener('beforeinstallprompt', capture); window.addEventListener('appinstalled', installed); window.addEventListener('online', sync); window.addEventListener('offline', sync) })
onUnmounted(() => { window.removeEventListener('beforeinstallprompt', capture); window.removeEventListener('appinstalled', installed); window.removeEventListener('online', sync); window.removeEventListener('offline', sync) })
</script>
<style scoped>.pwa-install{padding:15px;border:1px solid #dce7f4;border-radius:12px;background:#f7faff;color:#31577e}.pwa-install strong{font-size:13px}.pwa-install p{margin:7px 0;font-size:12px;line-height:1.7;overflow-wrap:anywhere}.pwa-install small{display:block;color:#7c8ea4;font-size:11px}.pwa-install button{margin:5px 0 10px;min-height:44px;border:0;border-radius:8px;padding:10px 18px;background:#2768d5;color:white}</style>
