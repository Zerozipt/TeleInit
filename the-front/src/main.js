import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from "@/router"
import axios from 'axios'

// 添加Node.js的global对象polyfill
window.global = window;

axios.defaults.baseURL = 'http://localhost:8080'

const app = createApp(App)
app.use(router)
app.use(ElementPlus)

// 添加一个全局错误处理器，帮助调试问题
app.config.errorHandler = (err, instance, info) => {
  console.error('Vue全局错误:', err)
  console.error('错误信息:', info)
  console.error('错误组件:', instance)
}

app.mount('#app')