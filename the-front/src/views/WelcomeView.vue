<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { logout, isUnauthorized } from '@/net'
import { getAuthData } from '@/utils/auth'

const router = useRouter()
const unauthorized = ref(true)
const username = ref('')

onMounted(() => {
  checkLoginStatus()
})

function checkLoginStatus() {
  unauthorized.value = isUnauthorized()
  if (!unauthorized.value) {
    const authData = getAuthData()
    username.value = authData?.username || '用户'
  }
}

function handleLogout() {
  logout(() => {
    unauthorized.value = true
    router.push({ name: 'welcome-login' })
  })
}

function goToLogin() {
  router.push({ name: 'welcome-login' })
}

function goToRegister() {
  router.push({ name: 'welcome-register' })
}
</script>

<template>
  <div class="welcome-container">
    <header class="header">
      <div class="logo">
        <h1>TeleInit聊天平台</h1>
      </div>
      <div class="nav-container">
        <div class="nav-left" v-if="unauthorized">
          <el-button type="primary" @click="goToLogin">登录</el-button>
          <el-button @click="goToRegister">注册</el-button>
        </div>
        <div class="nav-right" v-else>
          <span class="welcome-user">欢迎您，{{ username }}</span>
          <el-button type="danger" @click="handleLogout">退出登录</el-button>
        </div>
      </div>
    </header>

    <main class="main-content">
      <router-view />
    </main>

    <footer class="footer">
      <p>© 2024 TeleInit聊天平台 - 版权所有</p>
    </footer>
  </div>
</template>

<style scoped>
.welcome-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #1a1a1a;
}

.header {
  background-color: #2d2d2d;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.3);
  padding: 0 20px;
  height: 60px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: sticky;
  top: 0;
  z-index: 1000;
  border-bottom: 1px solid #404040;
}

.logo h1 {
  margin: 0;
  font-size: 24px;
  color: #409EFF;
}

.nav-container {
  display: flex;
  align-items: center;
}

.nav-left, .nav-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.welcome-user {
  margin-right: 10px;
  font-weight: 500;
  color: #ffffff;
}

.main-content {
  flex: 1;
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.footer {
  background-color: #2d2d2d;
  padding: 15px 0;
  text-align: center;
  color: #b0b0b0;
  border-top: 1px solid #404040;
}

/* 深色主题下的按钮样式定制 */
:deep(.el-button) {
  border-radius: 6px;
}

:deep(.el-button--primary) {
  background-color: #409EFF;
  border-color: #409EFF;
  color: #ffffff;
}

:deep(.el-button--primary:hover) {
  background-color: #66b1ff;
  border-color: #66b1ff;
}

:deep(.el-button--default) {
  background-color: #404040;
  border-color: #555555;
  color: #ffffff;
}

:deep(.el-button--default:hover) {
  background-color: #555555;
  border-color: #666666;
  color: #ffffff;
}

:deep(.el-button--danger) {
  background-color: #f56c6c;
  border-color: #f56c6c;
  color: #ffffff;
}

:deep(.el-button--danger:hover) {
  background-color: #f78989;
  border-color: #f78989;
}
</style>