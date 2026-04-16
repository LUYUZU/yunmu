<template>
  <div class="tab-content">
    <div class="content-header"><h2><i class="fas fa-database"></i> 数据管理</h2></div>
    <div class="filter-panel">
      <div class="filter-row">
        <div class="filter-item"><label>时间范围:</label><input type="date" v-model="dataFilter.startDate"><span>至</span><input type="date" v-model="dataFilter.endDate"></div>
        <div class="filter-item"><label>数据类型:</label>
          <select v-model="dataFilter.dataType">
            <option value="">全部</option><option value="behavior">行为数据</option><option value="location">定位数据</option><option value="posture">姿态数据</option><option value="step">步数数据</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" :class="{loading: loading.data}" @click="$emit('filter')"><i class="fas fa-filter"></i> 筛选</button>
          <button class="btn btn-success" :class="{loading: loading.data}" @click="$emit('export-all')"><i class="fas fa-file-export"></i> 全部导出</button>
          <button class="btn btn-info" :class="{loading: loading.data}" @click="$emit('export-filtered')"><i class="fas fa-file-export"></i> 导出筛选</button>
        </div>
      </div>
    </div>
    <div class="table-container">
      <table class="data-table">
        <thead><tr><th>时间</th><th>动物ID</th><th>行为</th><th>步数</th><th>位置</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="(r,i) in historyData" :key="i" :class="{alert: r.status==='异常'}">
            <td>{{ r.time }}</td><td>{{ r.animalId }}</td><td>{{ r.behavior }}</td><td>{{ r.steps }}</td><td>{{ r.location }}</td>
            <td><span class="table-status" :class="r.status==='正常'?'normal':'alert'">{{ r.status }}</span></td>
          </tr>
          <tr v-if="!historyData.length"><td colspan="6" class="table-empty">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
<script>export default { name: 'DataTab', props: { historyData: Array, dataFilter: Object, loading: Object } }
</script>