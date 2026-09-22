<template>
  <router-view />
</template>

<script setup lang="ts">
// App根组件
</script>

<style>
#app {
  width: 100%;
  height: 100vh;
  margin: 0;
  padding: 0;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: Inter, "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  color: #253047;
  background: #f5f7fb;
}

button, input, textarea { font: inherit; }

::-webkit-scrollbar { width: 8px; height: 8px; }
::-webkit-scrollbar-thumb { background: #cbd3df; border-radius: 8px; }
</style>
