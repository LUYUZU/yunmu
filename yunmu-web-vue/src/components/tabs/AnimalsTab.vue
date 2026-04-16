<template>
  <div class="tab-content">
    <div class="content-header"><h2><i class="fas fa-paw"></i> 动物监测</h2></div>
    <div class="filter-panel">
      <div class="filter-row">
        <div class="filter-item"><label>动物 ID:</label>
          <select v-model="animalFilter.animalId" @change="$emit('load')">
            <option value="">全部</option>
            <option v-for="a in animals" :key="a.id" :value="a.id">{{ a.id }}</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" :class="{loading: loading.animals}" @click="$emit('load')"><i class="fas fa-download"></i> 加载数据</button>
          <button class="btn btn-success" @click="$emit('export')"><i class="fas fa-file-export"></i> 导出</button>
        </div>
      </div>
    </div>
    <div class="table-container">
      <table class="data-table">
        <thead><tr><th>时间</th><th>动物ID</th><th>行为</th><th>体温</th><th>心率</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="(r,i) in animalRecords" :key="i" :class="{alert: r.status==='异常'}">
            <td>{{ r.time }}</td><td>{{ r.animalId }}</td><td>{{ r.behavior }}</td>
            <td :class="{'text-danger': parseFloat(r.temperature)>39}">{{ r.temperature||'暂无' }}{{ r.temperature?'°C':'' }}</td>
            <td :class="{'text-danger': parseFloat(r.heartRate)>90 || parseFloat(r.heartRate)<40}">{{ r.heartRate||'暂无' }}{{ r.heartRate?' bpm':'' }}</td>
            <td><span class="table-status" :class="r.status==='正常'?'normal':'alert'">{{ r.status }}</span></td>
          </tr>
          <tr v-if="!animalRecords.length"><td colspan="6" class="table-empty">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
<script>export default { name: 'AnimalsTab', props: { animals: Array, animalFilter: Object, animalRecords: Array, loading: Object } }
</script>