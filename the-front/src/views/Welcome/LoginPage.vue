<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/net'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const rememberMe = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度应为3-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应为6-20个字符', trigger: 'blur' }
  ]
}

const loginFormRef = ref()
const animateForm = ref(false)

onMounted(() => {
  setTimeout(() => {
    animateForm.value = true
  }, 100)
})

const handleLogin = () => {
  loginFormRef.value.validate((valid) => {
    if (valid) {
      loading.value = true
      login(
          loginForm.username,
          loginForm.password,
          rememberMe.value,
          () => {
            loading.value = false
            router.push('/index')
          },
          (message) => {
            ElMessage.error(message)
            loading.value = false
          }
      )
    }
  })
}

const goToRegister = () => {
  router.push({ name: 'welcome-register' })
}

const goToForget = () => {
  router.push({ name: 'welcome-forget' })
}
</script>

<template>
  <div class="login-container">
    <div class="background-shapes">
      <div class="shape shape-1"></div>
      <div class="shape shape-2"></div>
      <div class="shape shape-3"></div>
      <div class="shape shape-4"></div>
    </div>

    <div class="login-panel" :class="{ 'animate': animateForm }">
      <div class="login-side">
        <div class="side-content">
          <div class="logo-container">
            <div class="logo-icon">
              <i class="el-icon-chat-dot-round"></i>
            </div>
          </div>
          <h2>欢迎回来</h2>
          <p>登录您的账号，体验完整功能</p>
          <div class="illustration"></div>
        </div>
      </div>

      <div class="login-form-container">
        <div class="login-header">
          <h2>用户登录</h2>
          <p>请输入您的账号信息</p>
        </div>

        <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            label-position="top"
            class="login-form">

          <el-form-item label="用户名" prop="username">
            <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                class="custom-input">
              <template #prefix>
                <i class="el-icon-user"></i>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                show-password
                class="custom-input">
              <template #prefix>
                <i class="el-icon-lock"></i>
              </template>
            </el-input>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <el-button type="text" @click="goToForget" class="forget-btn">忘记密码？</el-button>
          </div>

          <el-button
              type="primary"
              :loading="loading"
              class="login-button"
              @click="handleLogin">
            <span v-if="!loading">登录</span>
            <span v-else>登录中...</span>
          </el-button>

          <div class="register-link">
            还没有账号？ <el-button type="text" @click="goToRegister" class="register-btn">立即注册</el-button>
          </div>
        </el-form>

        <div class="social-login">
          <p>其他登录方式</p>
          <div class="social-icons">
            <div class="social-icon weixin">
              <i class="el-icon-chat-dot-round"></i>
            </div>
            <div class="social-icon qq">
              <i class="el-icon-chat-round"></i>
            </div>
            <div class="social-icon weibo">
              <i class="el-icon-chat-line-round"></i>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4ecf7 100%);
  padding: 20px;
  position: relative;
  overflow: hidden;
}

.background-shapes .shape {
  position: absolute;
  border-radius: 50%;
  background: linear-gradient(to right, #4facfe 0%, #00f2fe 100%);
  animation: float 10s infinite ease-in-out;
}

.shape-1 {
  width: 300px;
  height: 300px;
  opacity: 0.1;
  left: -150px;
  top: -50px;
  animation-delay: 0s;
}

.shape-2 {
  width: 200px;
  height: 200px;
  opacity: 0.08;
  right: -80px;
  bottom: -50px;
  background: linear-gradient(to right, #667eea 0%, #764ba2 100%);
  animation-delay: 2s;
}

.shape-3 {
  width: 120px;
  height: 120px;
  opacity: 0.05;
  left: 10%;
  bottom: 10%;
  background: linear-gradient(to right, #ff8177 0%, #ff867a 100%);
  animation-delay: 4s;
}

.shape-4 {
  width: 150px;
  height: 150px;
  opacity: 0.08;
  right: 20%;
  top: 15%;
  background: linear-gradient(to right, #43e97b 0%, #38f9d7 100%);
  animation-delay: 6s;
}

@keyframes float {
  0% {
    transform: translateY(0px) rotate(0deg);
  }
  50% {
    transform: translateY(-20px) rotate(5deg);
  }
  100% {
    transform: translateY(0px) rotate(0deg);
  }
}

.login-panel {
  display: flex;
  width: 900px;
  max-width: 90%;
  min-height: 600px;
  background-color: #fff;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.6s ease;
}

.login-panel.animate {
  opacity: 1;
  transform: translateY(0);
}

.login-side {
  flex: 1;
  background: linear-gradient(135deg, #3f87fa 0%, #6549d5 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.side-content {
  position: relative;
  z-index: 2;
  text-align: center;
  padding: 0 30px;
}

.side-content h2 {
  font-size: 32px;
  margin-bottom: 10px;
  font-weight: 600;
}

.side-content p {
  font-size: 16px;
  opacity: 0.8;
  margin-bottom: 30px;
}

.logo-container {
  margin-bottom: 30px;
}

.logo-icon {
  width: 70px;
  height: 70px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.15);
}

.logo-icon i {
  font-size: 36px;
  color: #3f87fa;
}

.illustration {
  height: 200px;
  background: url('https://cdn.pixabay.com/photo/2019/10/09/07/28/development-4536630_960_720.png') no-repeat center center;
  background-size: contain;
  margin: 20px 0;
}

.login-form-container {
  flex: 1;
  padding: 50px 40px;
  display: flex;
  flex-direction: column;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h2 {
  font-size: 24px;
  color: #32325d;
  font-weight: 600;
  margin-bottom: 10px;
}

.login-header p {
  color: #8898aa;
  font-size: 15px;
}

.login-form {
  flex: 1;
  max-width: 380px;
  margin: 0 auto;
  width: 100%;
}

.custom-input :deep(.el-input__inner) {
  height: 48px;
  border-radius: 8px;
  border-color: #e7e9ec;
  padding-left: 45px;
  transition: all 0.3s;
}

.custom-input :deep(.el-input__inner:focus) {
  box-shadow: 0 0 0 2px rgba(63, 135, 250, 0.2);
  border-color: #3f87fa;
}

.custom-input :deep(.el-input__prefix) {
  left: 15px;
  color: #8898aa;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25px;
}

.forget-btn {
  padding: 0;
  font-size: 14px;
  color: #6549d5;
}

.login-button {
  width: 100%;
  padding: 12px 0;
  font-size: 16px;
  margin-bottom: 20px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3f87fa 0%, #6549d5 100%);
  border: none;
  height: 48px;
  transition: all 0.3s;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(101, 73, 213, 0.3);
}

.register-link {
  text-align: center;
  color: #8898aa;
  font-size: 14px;
}

.register-btn {
  padding: 0;
  font-size: 14px;
  color: #6549d5;
}

.social-login {
  margin-top: 20px;
  text-align: center;
}

.social-login p {
  font-size: 14px;
  color: #8898aa;
  position: relative;
  margin: 15px 0;
}

.social-login p::before,
.social-login p::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 30%;
  height: 1px;
  background-color: #e7e9ec;
}

.social-login p::before {
  left: 0;
}

.social-login p::after {
  right: 0;
}

.social-icons {
  display: flex;
  justify-content: center;
  gap: 15px;
}

.social-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  color: white;
}

.social-icon:hover {
  transform: translateY(-3px);
  box-shadow: 0 5px 10px rgba(0, 0, 0, 0.1);
}

.weixin {
  background: linear-gradient(135deg, #09b83e 0%, #09b83e 100%);
}

.qq {
  background: linear-gradient(135deg, #12b7f5 0%, #12b7f5 100%);
}

.weibo {
  background: linear-gradient(135deg, #e6162d 0%, #e6162d 100%);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-panel {
    flex-direction: column;
    height: auto;
    min-height: auto;
  }

  .login-side {
    padding: 40px 20px;
  }

  .illustration {
    height: 150px;
  }

  .login-form-container {
    padding: 30px 20px;
  }

  .side-content h2 {
    font-size: 24px;
  }
}

@media (max-width: 480px) {
  .login-panel {
    margin: 20px 0;
  }

  .login-side {
    padding: 30px 15px;
  }

  .illustration {
    height: 120px;
  }
}
</style>