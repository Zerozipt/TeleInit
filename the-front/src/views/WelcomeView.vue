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
        <h1>社区论坛</h1>
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
      <p>© 2024 社区论坛 - 版权所有</p>
    </footer>
  </div>
</template>

<style scoped>
.welcome-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}

.header {
  background-color: #fff;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  padding: 0 20px;
  height: 60px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: sticky;
  top: 0;
  z-index: 1000;
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
}

.main-content {
  flex: 1;
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.footer {
  background-color: #fff;
  padding: 15px 0;
  text-align: center;
  color: #909399;
}
</style>