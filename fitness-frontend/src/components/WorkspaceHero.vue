<template>
  <section class="wb-hero" :class="{ 'wb-hero--compact': compact }">
    <div class="wb-hero__copy">
      <div class="wb-hero__heading"><slot name="heading" /></div>
      <div v-if="$slots.actions" class="wb-hero__actions"><slot name="actions" /></div>
    </div>
    <div class="wb-hero__art" aria-hidden="true">
      <div class="wb-hero__orbit" />
      <span class="wb-hero__star wb-hero__star--one">✦</span>
      <span class="wb-hero__star wb-hero__star--two">✧</span>
      <span class="wb-hero__star wb-hero__star--three">✦</span>
      <div class="wb-hero__portrait"><img :src="characterUrl" alt="" draggable="false" decoding="async" /></div>
      <span class="wb-hero__caption">KEEP MOVING</span>
    </div>
    <div v-if="$slots.footer" class="wb-hero__footer"><slot name="footer" /></div>
  </section>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
const characterUrl = `${import.meta.env.BASE_URL}hero-character.jpg`
</script>

<style scoped>
.wb-hero {
  position: relative; isolation: isolate; display: grid; grid-template-columns: minmax(0, 1fr) 190px;
  align-items: center; gap: 12px 24px; min-height: 178px; padding: 25px 30px; margin-bottom: 22px;
  border: 1px solid #cbdff7; border-radius: 22px; overflow: hidden;
  background: linear-gradient(112deg, #f8fcff 0%, #e7f2ff 52%, #d5e9fd 82%, #e6eaff 100%);
  box-shadow: 0 10px 32px #2d639710, inset 0 1px 0 #fff;
}
.wb-hero::before, .wb-hero::after { content: ''; position: absolute; z-index: -1; pointer-events: none; }
.wb-hero::before { inset: 0; background-image: radial-gradient(#72a5d44d .8px, transparent 1px); background-size: 7px 7px; mask-image: linear-gradient(90deg, transparent 35%, #000); opacity: .48; }
.wb-hero::after { width: 300px; height: 300px; right: 85px; top: -165px; border: 1px solid #fff; border-radius: 50%; box-shadow: 0 0 0 26px #ffffff40, 0 0 0 53px #ffffff25; }
.wb-hero__copy { min-width: 0; z-index: 1; }
.wb-hero__heading :deep(h1), .wb-hero__heading :deep(h2) { margin: 7px 0 8px; color: #183453; font-size: clamp(24px, 2.2vw, 32px); font-weight: 800; line-height: 1.35; letter-spacing: -.7px; text-wrap: balance; }
.wb-hero__heading :deep(span:first-child) { color: #3975b7; font-size: 10px; font-weight: 800; letter-spacing: 1.8px; line-height: 1.5; }
.wb-hero__heading :deep(p) { margin: 0; max-width: 800px; color: #577392; font-size: 13px; line-height: 1.7; }
.wb-hero__actions { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 15px; }
.wb-hero__actions :deep(> div) { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; min-width: 0; }
.wb-hero__art { position: relative; pointer-events: none; width: 190px; height: 155px; justify-self: end; }
.wb-hero__portrait { position: absolute; width: 123px; height: 151px; right: 28px; top: 1px; transform: rotate(5deg); border: 3px solid #ffffffdb; border-radius: 18px 5px 18px 5px; overflow: hidden; background: #e8f2ff; box-shadow: 0 10px 25px #294e832e; animation: wb-float 7s ease-in-out infinite; }
.wb-hero__portrait img { width: 100%; height: 100%; object-fit: cover; object-position: 50% 22%; mix-blend-mode: multiply; }
.wb-hero__orbit { position: absolute; width: 162px; height: 162px; right: 8px; top: -3px; border: 1px solid #77a5d56b; border-radius: 50%; box-shadow: 0 0 0 12px #ffffff36, 0 0 0 24px #ffffff24; }
.wb-hero__star { position: absolute; color: #5893d6; text-shadow: 0 0 12px #fff; animation: wb-twinkle 4s ease-in-out infinite; }
.wb-hero__star--one { left: 3px; top: 19px; font-size: 28px; }
.wb-hero__star--two { right: 0; top: 46px; font-size: 30px; animation-delay: -1.8s; }
.wb-hero__star--three { left: 16px; bottom: 4px; font-size: 18px; animation-delay: -2.8s; }
.wb-hero__caption { position: absolute; bottom: -3px; right: 6px; padding: 5px 10px; transform: rotate(-5deg); background: #ffffffed; border: 1px solid #accaf0; border-radius: 5px; color: #356390; font-size: 9px; font-weight: 800; letter-spacing: 1.5px; box-shadow: 0 4px 10px #3a64911a; }
.wb-hero__footer { grid-column: 1 / -1; padding-top: 12px; border-top: 1px solid #91b3d533; z-index: 1; }
.wb-hero--compact { min-height: 145px; padding: 20px 26px; grid-template-columns: minmax(0, 1fr) 132px; }
.wb-hero--compact .wb-hero__art { width: 132px; height: 117px; }
.wb-hero--compact .wb-hero__portrait { width: 89px; height: 113px; right: 20px; border-width: 2px; }
.wb-hero--compact .wb-hero__orbit { width: 118px; height: 118px; right: 0; }
.wb-hero--compact .wb-hero__heading :deep(h1), .wb-hero--compact .wb-hero__heading :deep(h2) { font-size: clamp(22px, 2vw, 27px); }
.wb-hero--compact .wb-hero__caption { font-size: 8px; padding: 4px 6px; }
@keyframes wb-float { 0%, 100% { transform: translateY(0) rotate(5deg); } 50% { transform: translateY(-5px) rotate(3deg); } }
@keyframes wb-twinkle { 0%, 100% { opacity: .4; transform: scale(.8); } 50% { opacity: .95; transform: scale(1); } }
@media (max-width: 760px) { .wb-hero, .wb-hero--compact { padding: 22px; gap: 15px; grid-template-columns: minmax(0, 1fr) 100px; } .wb-hero__art { width: 100px; height: 130px; } .wb-hero__portrait { width: 90px; height: 124px; right: 3px; } .wb-hero__orbit { width: 110px; height: 110px; right: 0; } .wb-hero__caption { right: -5px; } .wb-hero__heading :deep(h1), .wb-hero__heading :deep(h2) { font-size: 24px; } }
@media (max-width: 540px) { .wb-hero, .wb-hero--compact { grid-template-columns: minmax(0, 1fr); padding: 20px 18px; border-radius: 17px; min-height: 0; } .wb-hero__art { display: none; } .wb-hero__heading :deep(h1), .wb-hero__heading :deep(h2) { font-size: 22px; } }
@media (prefers-reduced-motion: reduce) { .wb-hero__portrait, .wb-hero__star { animation: none; } }
</style>
