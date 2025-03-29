<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/net'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const rememberMe = ref(false)
const loginFormRef = ref()
const animateForm = ref(false)

const loginForm = reactive({
  email: '',
  password: ''
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应为6-20个字符', trigger: 'blur' }
  ]
}

//在组件挂载时，设置动画效果
//setTimeout的作用是设置一个定时器，定时器会在100毫秒后执行，执行完后将animateForm的值设置为true
//这样就可以实现动画效果
onMounted(() => {
  setTimeout(() => animateForm.value = true, 100)
})

const handleLogin = () => {
  loginFormRef.value.validate((valid) => {
    if (valid) {
      loading.value = true
      login(
        loginForm.email,
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

const goToRegister = () => router.push({ name: 'welcome-register' })
const goToForget = () => router.push({ name: 'welcome-forget' })
</script>

<template>
  <div class="login-container">
    <div class="background"></div>
    
    <div class="login-panel" :class="{ 'animate': animateForm }">
      <div class="login-side">
        <div class="logo-icon"><i class="el-icon-chat-dot-round"></i></div>
        <h2>欢迎回来</h2>
        <p>登录您的账号，体验完整功能</p>
        <div class="illustration"></div>
      </div>

      <div class="login-form-container">
        <h2>用户登录</h2>
        <p>请输入您的账号信息</p>

        <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            label-position="top"
            class="login-form">

          <el-form-item label="邮箱" prop="email">
            <el-input
                v-model="loginForm.email"
                placeholder="请输入邮箱">
              <template #prefix><i class="el-icon-user"></i></template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                show-password>
              <template #prefix><i class="el-icon-lock"></i></template>
            </el-input>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <el-button type="text" @click="goToForget">忘记密码？</el-button>
          </div>

          <el-button
              type="primary"
              :loading="loading"
              class="login-button"
              @click="handleLogin">
            {{ loading ? '登录中...' : '登录' }}
          </el-button>

          <div class="register-link">
            还没有账号？ <el-button type="text" @click="goToRegister">立即注册</el-button>
          </div>
        </el-form>

        <div class="social-login">
          <p>其他登录方式</p>
          <div class="social-icons">
            <div class="social-icon weixin"><i class="el-icon-chat-dot-round"></i></div>
            <div class="social-icon qq"><i class="el-icon-chat-round"></i></div>
            <div class="social-icon weibo"><i class="el-icon-chat-line-round"></i></div>
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
  background: #f5f7fa;
  padding: 20px;
  position: relative;
  overflow: hidden;
}

.background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4ecf7 100%);
  z-index: -1;
}

.login-panel {
  display: flex;
  width: 900px;
  max-width: 90%;
  min-height: 550px;
  background-color: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.5s ease;
}

.login-panel.animate {
  opacity: 1;
  transform: translateY(0);
}

.login-side {
  flex: 1;
  background: linear-gradient(135deg, #3f87fa 0%, #6549d5 100%);
  color: white;
  padding: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.logo-icon {
  width: 60px;
  height: 60px;
  background: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
}

.logo-icon i {
  font-size: 30px;
  color: #3f87fa;
}

.login-side h2 {
  font-size: 28px;
  margin-bottom: 10px;
  font-weight: 600;
}

.login-side p {
  font-size: 15px;
  opacity: 0.9;
  margin-bottom: 30px;
}

.illustration {
  height: 180px;
  width: 100%;
  background: url('https://cdn.pixabay.com/photo/2019/10/09/07/28/development-4536630_960_720.png') no-repeat center center;
  background-size: contain;
  margin-top: auto;
}

.login-form-container {
  flex: 1;
  padding: 40px;
  display: flex;
  flex-direction: column;
}

.login-form-container h2 {
  font-size: 22px;
  color: #32325d;
  font-weight: 600;
  margin-bottom: 8px;
  text-align: center;
}

.login-form-container p {
  color: #8898aa;
  font-size: 14px;
  margin-bottom: 25px;
  text-align: center;
}

.login-form {
  max-width: 360px;
  margin: 0 auto;
  width: 100%;
}

.el-input :deep(.el-input__inner) {
  height: 44px;
  border-radius: 8px;
  transition: all 0.3s;
}

.el-input :deep(.el-input__prefix) {
  left: 12px;
  color: #8898aa;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #3f87fa 0%, #6549d5 100%);
  border: none;
  transition: all 0.3s;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(101, 73, 213, 0.25);
}

.register-link {
  text-align: center;
  color: #8898aa;
  font-size: 14px;
  margin-bottom: 20px;
}

.social-login {
  margin-top: auto;
  text-align: center;
}

.social-login p {
  position: relative;
  margin: 15px 0;
}

.social-login p::before,
.social-login p::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 25%;
  height: 1px;
  background-color: #e7e9ec;
}

.social-login p::before { left: 0; }
.social-login p::after { right: 0; }

.social-icons {
  display: flex;
  justify-content: center;
  gap: 15px;
}

.social-icon {
  width: 36px;
  height: 36px;
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
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
}

.weixin { background: #09b83e; }
.qq { background: #12b7f5; }
.weibo { background: #e6162d; }

@media (max-width: 768px) {
  .login-panel {
    flex-direction: column;
    min-height: auto;
  }
  
  .login-side {
    padding: 30px 20px;
  }
  
  .illustration {
    height: 120px;
    margin: 10px 0;
  }
  
  .login-form-container {
    padding: 30px 20px;
  }
}
</style>