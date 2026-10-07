import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import App from './App.vue'
import './assets/css/variables.css'
import './assets/css/base.css'
import './assets/css/components.css'
import './assets/css/dashboard.css'
import './assets/css/map.css'
import './assets/css/modules.css'
import './assets/css/responsive.css'

Vue.use(ElementUI, { size: 'medium' })
Vue.config.productionTip = false

new Vue({
  render: h => h(App)
}).$mount('#app')
