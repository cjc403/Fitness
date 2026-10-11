<template><TrainingPlanPanel :initial-member-id="memberId" :initial-member-name="memberName"/></template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TrainingPlanPanel from '@/components/TrainingPlanPanel.vue'
const route=useRoute()
const memberId=computed(()=>{const raw=route.query.memberId;if(typeof raw!=='string'||!/^\d+$/.test(raw))return undefined;const id=Number(raw);return Number.isSafeInteger(id)&&id>0?id:undefined})
const memberName=computed(()=>typeof route.query.memberName==='string'?route.query.memberName.slice(0,100):'')
</script>
