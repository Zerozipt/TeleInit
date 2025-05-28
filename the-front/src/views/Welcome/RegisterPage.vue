<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/net'
import { ElMessage } from 'element-plus'
import { askVerifyCode } from '@/net'

const router = useRouter()
const loading = ref(false)
const sendingCode = ref(false)
const codeCountdown = ref(0)
const registerFormRef = ref()
const animateForm = ref(false)

const form = reactive({
    username: '',
    password: '',
    password_repeat: '',
    email: '',
    code: ''
})

const validateUsername = (rule, value, callback) => {
    if(value.length < 3 || value.length > 16){
        callback(new Error('用户名长度应为3-16个字符'))
    }else if(!/^[a-zA-Z0-9\u4e00-\u9fa5]+$/.test(value)){
        callback(new Error('用户名只能包含数字、字母、中文'))
    }else{
        callback()
    }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { validator: validateUsername, trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应为6-20个字符', trigger: 'blur' }
  ],
  password_repeat: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      }, 
      trigger: 'blur' 
    }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { min: 6, max: 6, message: '验证码长度不正确', trigger: 'blur' }
  ]
}

onMounted(() => {
  setTimeout(() => animateForm.value = true, 100)
})

const handleRegister = () => {
  registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await register(
          form.username,
          form.password,
          form.email,
          form.code,
          () => {
            ElMessage.success('注册成功！')
            router.push('/login')
          },
          (message) => {
            ElMessage.error(message)
          }
        )
      } catch (error) {
        ElMessage.error('注册过程中发生错误')
      } finally {
        loading.value = false
      }
    }
  })
}

const sendVerificationCode = () => {
  if (!form.email) {
    ElMessage.warning('请先输入邮箱地址')
    return
  }

  sendingCode.value = true
  askVerifyCode('register', form.email, (message) => {
    console.log(message)
    if(message == "success"){
      ElMessage.success('验证码已发送至 ' + form.email)
      startCountdown()
    }else{
      ElMessage.error(message)
    }
    sendingCode.value = false
  })
}

const startCountdown = () => {
  codeCountdown.value = 60
  const timer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

const goToLogin = () => router.push({ name: 'welcome-login' })
</script>

<template>
  <div class="register-container">
    <div class="background"></div>
    
    <div class="register-panel" :class="{ 'animate': animateForm }">
      <div class="register-side">
        <div class="logo-icon">🚀</div>
        <h2>欢迎加入</h2>
        <p>创建您的账号，开始精彩体验</p>
        <div class="illustration">
          <div class="chat-features">
            <div class="feature feature-1">💬 即时聊天</div>
            <div class="feature feature-2">👥 群组讨论</div>
            <div class="feature feature-3">📁 文件分享</div>
          </div>
        </div>
      </div>

      <div class="register-form-container">
        <h2>用户注册</h2>
        <p>请填写以下信息完成注册</p>

        <el-form
            ref="registerFormRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="register-form">

          <el-form-item label="用户名" prop="username">
            <el-input
                v-model="form.username"
                placeholder="请输入用户名"
                size="large">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="确认密码" prop="password_repeat">
            <el-input
                v-model="form.password_repeat"
                type="password"
                placeholder="请再次输入密码"
                size="large"
                show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="邮箱" prop="email">
            <el-input
                v-model="form.email"
                placeholder="请输入邮箱"
                size="large">
              <template #prefix><el-icon><Message /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="验证码" prop="code">
            <div class="verification-code">
              <el-input
                  v-model="form.code"
                  placeholder="请输入验证码"
                  size="large">
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
              <el-button 
                  type="primary" 
                  @click="sendVerificationCode"
                  :disabled="codeCountdown > 0"
                  :loading="sendingCode"
                  size="large">
                {{ codeCountdown > 0 ? `${codeCountdown}s后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
  
          <el-button
              type="primary"
              :loading="loading"
              size="large"
              class="register-button"
              @click="handleRegister">
            {{ loading ? '注册中...' : '立即注册' }}
          </el-button>

          <div class="login-link">
            已有账号？ <el-button type="text" @click="goToLogin" class="login-btn">立即登录</el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #1a1a1a;
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
  background: linear-gradient(135deg, #1a1a1a 0%, #2d2d2d 100%);
  z-index: -1;
}

.register-panel {
  display: flex;
  width: 900px;
  max-width: 90%;
  min-height: 600px;
  background-color: #2d2d2d;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.5s ease;
  border: 1px solid #404040;
}

.register-panel.animate {
  opacity: 1;
  transform: translateY(0);
}

.register-side {
  flex: 1;
  background: linear-gradient(135deg, #1db8e8 0%, #409EFF 100%);
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
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  font-size: 30px;
  backdrop-filter: blur(10px);
}

.register-side h2 {
  font-size: 28px;
  margin-bottom: 10px;
  font-weight: 600;
}

.register-side p {
  font-size: 15px;
  opacity: 0.9;
  margin-bottom: 30px;
}

.illustration {
  height: 180px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: auto;
}

.chat-features {
  position: relative;
  width: 200px;
  height: 150px;
}

.feature {
  position: absolute;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  padding: 8px 12px;
  backdrop-filter: blur(10px);
  font-size: 12px;
  animation: bounce 4s ease-in-out infinite;
}

.feature-1 {
  top: 20px;
  left: 10px;
  animation-delay: 0s;
}

.feature-2 {
  top: 60px;
  right: 10px;
  animation-delay: 1.5s;
}

.feature-3 {
  bottom: 20px;
  left: 30px;
  animation-delay: 3s;
}

@keyframes bounce {
  0%, 100% { transform: translateY(0px) scale(1); }
  50% { transform: translateY(-8px) scale(1.05); }
}

.register-form-container {
  flex: 1;
  padding: 40px;
  display: flex;
  flex-direction: column;
  background: #2d2d2d;
}

.register-form-container h2 {
  font-size: 22px;
  color: #ffffff;
  font-weight: 600;
  margin-bottom: 8px;
  text-align: center;
}

.register-form-container p {
  color: #b0b0b0;
  font-size: 14px;
  margin-bottom: 25px;
  text-align: center;
}

.register-form {
  max-width: 360px;
  margin: 0 auto;
  width: 100%;
}

:deep(.el-form-item__label) {
  color: #ffffff !important;
}

:deep(.el-input__inner) {
  background-color: #404040 !important;
  border-color: #555555 !important;
  color: #ffffff !important;
}

:deep(.el-input__inner::placeholder) {
  color: #999999 !important;
}

:deep(.el-input__prefix) {
  color: #999999 !important;
}

.verification-code {
  display: flex;
  gap: 10px;
}

.verification-code .el-input {
  flex: 1;
}

.register-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1db8e8 0%, #409EFF 100%);
  border: none;
  transition: all 0.3s;
}

.register-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(29, 184, 232, 0.25);
}

.login-link {
  text-align: center;
  color: #b0b0b0;
  font-size: 14px;
  margin-bottom: 20px;
}

.login-btn {
  color: #409EFF !important;
  padding: 0 !important;
}

.login-btn:hover {
  color: #66b1ff !important;
}

@media (max-width: 768px) {
  .register-panel {
    flex-direction: column;
    min-height: auto;
  }
  
  .register-side {
    padding: 30px 20px;
  }
  
  .illustration {
    height: 120px;
    margin: 10px 0;
  }
  
  .register-form-container {
    padding: 30px 20px;
  }

  .verification-code {
    flex-direction: column;
  }
}
</style>
