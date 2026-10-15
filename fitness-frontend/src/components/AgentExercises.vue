<template>
  <div class="exercise-editor">
    <article v-for="(item, index) in modelValue" :key="index">
      <div class="exercise-head"><strong>动作 {{ index + 1 }}</strong><button type="button" @click="remove(index)">移除</button></div>
      <label>动作名称<input :value="item.name" maxlength="100" placeholder="例如：深蹲" @input="update(index, 'name', ($event.target as HTMLInputElement).value)" /></label>
      <label>训练方式<select :value="item.mode" @change="update(index, 'mode', ($event.target as HTMLSelectElement).value)"><option value="STRENGTH">力量训练</option><option value="DURATION">时长训练</option></select></label>
      <div v-if="item.mode === 'STRENGTH'" class="exercise-numbers">
        <label>组数<input type="number" min="1" max="20" step="1" :value="item.sets" @input="number(index, 'sets', $event)" /></label>
        <label>每组次数<input type="number" min="1" max="200" step="1" :value="item.reps" @input="number(index, 'reps', $event)" /></label>
        <label>重量 kg<input type="number" min="0" max="500" step="0.01" :value="item.weightKg" @input="number(index, 'weightKg', $event)" /></label>
      </div>
      <label v-else>时长（分钟）<input type="number" min="1" max="240" step="1" :value="item.minutes" @input="number(index, 'minutes', $event)" /></label>
      <label>动作备注<input :value="item.note" maxlength="300" @input="update(index, 'note', ($event.target as HTMLInputElement).value)" /></label>
    </article>
    <button type="button" :disabled="modelValue.length >= 20" @click="add">＋ 添加训练动作</button>
  </div>
</template>
<script setup lang="ts">
type Exercise = { name: string; mode: string; sets: number | null; reps: number | null; weightKg: number | null; minutes: number | null; note: string }
const props = defineProps<{ modelValue: Exercise[] }>()
const emit = defineEmits<{ 'update:modelValue': [Exercise[]] }>()
const add = () => { if (props.modelValue.length < 20) emit('update:modelValue', [...props.modelValue, { name: '', mode: 'STRENGTH', sets: 3, reps: 12, weightKg: null, minutes: null, note: '' }]) }
const remove = (index: number) => emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
const update = (index: number, field: string, value: string | number | null) => emit('update:modelValue', props.modelValue.map((item, i) => {
  if (i !== index) return item
  const next = { ...item, [field]: value }
  if (field === 'mode') { next.sets = value === 'STRENGTH' ? 3 : null; next.reps = value === 'STRENGTH' ? 12 : null; next.weightKg = null; next.minutes = value === 'DURATION' ? 10 : null }
  return next
}))
const number = (index: number, field: string, event: Event) => { const value = (event.target as HTMLInputElement).value; update(index, field, value === '' ? null : Number(value)) }
</script>
<style scoped>
.exercise-editor{display:grid;gap:10px}.exercise-editor article{display:grid;gap:10px;border:1px solid #dce7f6;background:#f7faff;border-radius:12px;padding:12px}.exercise-head{display:flex;justify-content:space-between;align-items:center}.exercise-editor label{display:grid;gap:5px;font-size:12px;color:#576b86}.exercise-numbers{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.exercise-editor input,.exercise-editor select{width:100%;min-width:0;min-height:42px;border:1px solid #cfddef;border-radius:8px;padding:8px;background:#fff;color:#243d5b;font-size:16px}.exercise-editor button{min-height:40px;border:1px solid #cfdef1;border-radius:8px;background:#fff;color:#2866b6;cursor:pointer}
</style>
