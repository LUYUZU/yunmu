<template>
  <div class="tab-content">
    <div class="content-header">
      <h2><i class="fas fa-tachometer-alt"></i> 系统概览</h2>
      <div class="header-actions">
        <button class="btn btn-primary" :class="{loading}" @click="$emit('refresh')"><i class="fas fa-plug"></i> 检查后端连接</button>
        <button class="btn btn-success" :class="{loading}" @click="$emit('mock')"><i class="fas fa-bolt"></i> 生成模拟数据</button>
        <button class="btn btn-info" :class="{loading}" @click="$emit('refresh-all')"><i class="fas fa-sync-alt"></i> 刷新</button>
      </div>
    </div>
    <div class="stats-cards">
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#667eea,#764ba2)"><i class="fas fa-paw"></i></div><div class="stat-info"><div class="stat-value">{{ stats.totalAnimals }}</div><div class="stat-label">监测动物总数</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#11998e,#38ef7d)"><i class="fas fa-check-circle"></i></div><div class="stat-info"><div class="stat-value">{{ stats.normal }}</div><div class="stat-label">正常状态</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#eb3349,#f45c43)"><i class="fas fa-exclamation-triangle"></i></div><div class="stat-info"><div class="stat-value">{{ stats.alert }}</div><div class="stat-label">异常预警</div></div></div>
      <div class="stat-card"><div class="stat-icon" style="background:linear-gradient(135deg,#4facfe,#00f2fe)"><i class="fas fa-database"></i></div><div class="stat-info"><div class="stat-value">{{ stats.dataReceived }}</div><div class="stat-label">数据接收数</div></div></div>
    </div>
    <div class="dashboard-card">
      <div class="card-header"><h3><i class="fas fa-stream"></i> 实时数据流</h3></div>
      <div class="data-stream">
        <div class="stream-empty" v-if="!dataStreamLogs.length">暂无数据流</div>
        <div v-for="log in dataStreamLogs" :key="log.id" class="stream-log">
          <span class="log-time">{{ log.time }}</span>
          <span class="log-source">[{{ log.source }}]</span>
          <span class="log-message">{{ log.message }}</span>
        </div>
      </div>
    </div>
    <div class="dashboard-card">
      <div class="card-header"><h3><i class="fas fa-heartbeat"></i> 实时监测</h3></div>
      <div class="animal-cards-grid">
        <div v-for="animal in animals" :key="animal.id" class="animal-card" :class="{alert: animal.status==='alert'}">
          <div class="animal-header">
            <span class="animal-id"><i :class="animal.type==='cow'?'fas fa-cow':'fas fa-sheep'"></i> {{ animal.id }}</span>
            <span class="animal-status" :class="animal.status==='normal'?'status-normal':'status-alert'">{{ animal.status==='normal'?'正常':'异常' }}</span>
          </div>
          <div class="animal-data">
            <div class="data-item"><span class="data-label"><i class="fas fa-thermometer-half"></i> 体温</span><span class="data-value" :class="{'text-danger': parseFloat(animal.temperature)>39}">{{ animal.temperature||'暂无' }}{{ animal.temperature?'°C':'' }}</span></div>
            <div class="data-item"><span class="data-label"><i class="fas fa-heart"></i> 心率</span><span class="data-value" :class="{'text-danger': parseFloat(animal.heartRate)>90 || parseFloat(animal.heartRate)<40}">{{ animal.heartRate||'暂无' }}{{ animal.heartRate?' bpm':'' }}</span></div>
            <div class="data-item"><span class="data-label"><i class="fas fa-running"></i> 步数</span><span class="data-value">{{ animal.steps }}</span></div>
            <div class="data-item"><span class="data-label"><i class="fas fa-walking"></i> 行为</span><span class="data-value">{{ animal.behavior }}</span></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>export default { name: 'OverviewTab', props: { stats: Object, animals: Array, dataStreamLogs: Array, loading: Object } }
</script>