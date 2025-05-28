<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/net'
import { ElMessage } from 'element-plus'
import { sendPasswordChangeCode, updatePassword } from '@/api/settingsApi'

const router = useRouter()
const loading = ref(false)
const rememberMe = ref(false)
const loginFormRef = ref()
const forgetFormRef = ref()
const animateForm = ref(false)
const showForgetModal = ref(false)
const sendingCode = ref(false)
const updatingPassword = ref(false)
const codeCountdown = ref(0)

const loginForm = reactive({
  email: '',
  password: ''
})

const forgetForm = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: ''
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

const forgetRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { 
      validator: (rule, value, callback) => {
        if (value !== forgetForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      }, 
      trigger: 'blur' 
    }
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

const openForgetModal = () => {
  showForgetModal.value = true
  forgetForm.email = loginForm.email // 预填写邮箱
}

const closeForgetModal = () => {
  showForgetModal.value = false
  forgetForm.email = ''
  forgetForm.verificationCode = ''
  forgetForm.newPassword = ''
  forgetForm.confirmPassword = ''
  codeCountdown.value = 0
}

const sendCode = async () => {
  if (!forgetForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }

  try {
    sendingCode.value = true
    const response = await sendPasswordChangeCode(forgetForm.email)
    
    if (response.data && response.data.code === 200) {
      ElMessage.success('验证码发送成功')
      startCountdown()
    } else {
      ElMessage.error(response.data?.message || '验证码发送失败')
    }
  } catch (error) {
    ElMessage.error('验证码发送失败')
    console.error('Send code error:', error)
  } finally {
    sendingCode.value = false
  }
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

const resetPassword = async () => {
  try {
    await forgetFormRef.value.validate()
    
    updatingPassword.value = true
    
    const response = await updatePassword(
      forgetForm.email,
      forgetForm.verificationCode,
      forgetForm.newPassword
    )
    
    if (response.data && response.data.code === 200) {
      ElMessage.success('密码重置成功，请使用新密码登录')
      closeForgetModal()
      loginForm.email = forgetForm.email
      loginForm.password = ''
    } else {
      ElMessage.error(response.data?.message || '密码重置失败')
    }
  } catch (error) {
    if (error !== false) {
      ElMessage.error('密码重置失败')
      console.error('Reset password error:', error)
    }
  } finally {
    updatingPassword.value = false
  }
}

const goToRegister = () => router.push({ name: 'welcome-register' })
</script>

<template>
  <div class="login-container">
    <div class="background"></div>
    
    <div class="login-panel" :class="{ 'animate': animateForm }">
      <div class="login-side">
        <div class="logo-icon">💬</div>
        <h2>欢迎回来</h2>
        <p>登录您的账号，体验完整功能</p>
        <div class="illustration">
          <div class="chat-bubbles">
            <div class="bubble bubble-1">Hi</div>
            <div class="bubble bubble-2">Hello!</div>
            <div class="bubble bubble-3">Welcome</div>
          </div>
        </div>
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
                placeholder="请输入邮箱"
                size="large">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password>
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <el-button type="text" @click="openForgetModal" class="forget-btn">忘记密码？</el-button>
          </div>

          <el-button
              type="primary"
              :loading="loading"
              size="large"
              class="login-button"
              @click="handleLogin">
            {{ loading ? '登录中...' : '登录' }}
          </el-button>

          <div class="register-link">
            还没有账号？ <el-button type="text" @click="goToRegister" class="register-btn">立即注册</el-button>
          </div>
        </el-form>

        <div class="social-login">
          <p>其他登录方式</p>
          <div class="social-icons">
            <div class="social-icon weixin">微</div>
            <div class="social-icon qq">Q</div>
            <div class="social-icon weibo">微</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 忘记密码弹窗 -->
    <el-dialog
        v-model="showForgetModal"
        title="重置密码"
        width="400px"
        :close-on-click-modal="false"
        class="forget-dialog">
      
      <el-form
          ref="forgetFormRef"
          :model="forgetForm"
          :rules="forgetRules"
          label-width="80px">
        
        <el-form-item label="邮箱" prop="email">
          <el-input
              v-model="forgetForm.email"
              placeholder="请输入邮箱"
              size="large" />
        </el-form-item>
        
        <el-form-item label="验证码" prop="verificationCode">
          <div class="code-input-group">
            <el-input
                v-model="forgetForm.verificationCode"
                placeholder="请输入验证码"
                size="large" />
            <el-button
                @click="sendCode"
                :disabled="codeCountdown > 0"
                :loading="sendingCode"
                type="primary"
                size="large">
              {{ codeCountdown > 0 ? `${codeCountdown}s后重发` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        
        <el-form-item label="新密码" prop="newPassword">
          <el-input
              v-model="forgetForm.newPassword"
              type="password"
              placeholder="请输入新密码"
              size="large"
              show-password />
        </el-form-item>
        
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
              v-model="forgetForm.confirmPassword"
              type="password"
              placeholder="请确认新密码"
              size="large"
              show-password />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeForgetModal" size="large">取消</el-button>
          <el-button
              type="primary"
              @click="resetPassword"
              :loading="updatingPassword"
              size="large">
            {{ updatingPassword ? '重置中...' : '重置密码' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.login-container {
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

.login-panel {
  display: flex;
  width: 900px;
  max-width: 90%;
  min-height: 550px;
  background-color: #2d2d2d;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  opacity: 0;
  transform: translateY(20px);
  transition: all 0.5s ease;
  border: 1px solid #404040;
}

.login-panel.animate {
  opacity: 1;
  transform: translateY(0);
}

.login-side {
  flex: 1;
  background: linear-gradient(135deg, #409EFF 0%, #409EFF 100%);
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
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: auto;
}

.chat-bubbles {
  position: relative;
  width: 200px;
  height: 150px;
}

.bubble {
  position: absolute;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  padding: 8px 16px;
  backdrop-filter: blur(10px);
  font-size: 14px;
  animation: float 3s ease-in-out infinite;
}

.bubble-1 {
  top: 20px;
  left: 20px;
  animation-delay: 0s;
}

.bubble-2 {
  top: 60px;
  right: 20px;
  animation-delay: 1s;
}

.bubble-3 {
  bottom: 20px;
  left: 40px;
  animation-delay: 2s;
}

@keyframes float {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
}

.login-form-container {
  flex: 1;
  padding: 40px;
  display: flex;
  flex-direction: column;
  background: #2d2d2d;
}

.login-form-container h2 {
  font-size: 22px;
  color: #ffffff;
  font-weight: 600;
  margin-bottom: 8px;
  text-align: center;
}

.login-form-container p {
  color: #b0b0b0;
  font-size: 14px;
  margin-bottom: 25px;
  text-align: center;
}

.login-form {
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

:deep(.el-checkbox__label) {
  color: #ffffff !important;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.forget-btn {
  color: #409EFF !important;
  padding: 0 !important;
}

.forget-btn:hover {
  color: #66b1ff !important;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409EFF 0%, #409EFF 100%);
  border: none;
  transition: all 0.3s;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.25);
}

.register-link {
  text-align: center;
  color: #b0b0b0;
  font-size: 14px;
  margin-bottom: 20px;
}

.register-btn {
  color: #409EFF !important;
  padding: 0 !important;
}

.register-btn:hover {
  color: #66b1ff !important;
}

.social-login {
  margin-top: auto;
  text-align: center;
}

.social-login p {
  position: relative;
  margin: 15px 0;
  color: #b0b0b0;
}

.social-login p::before,
.social-login p::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 25%;
  height: 1px;
  background-color: #555555;
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
  font-weight: bold;
  font-size: 14px;
}

.social-icon:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.3);
}

.weixin { background: #09b83e; }
.qq { background: #12b7f5; }
.weibo { background: #e6162d; }

/* 忘记密码弹窗样式 */
:deep(.forget-dialog .el-dialog) {
  background-color: #2d2d2d !important;
  border: 1px solid #404040 !important;
}

:deep(.forget-dialog .el-dialog__header) {
  background-color: #2d2d2d !important;
  border-bottom: 1px solid #404040 !important;
}

:deep(.forget-dialog .el-dialog__title) {
  color: #ffffff !important;
}

:deep(.forget-dialog .el-dialog__body) {
  background-color: #2d2d2d !important;
  color: #ffffff !important;
}

:deep(.forget-dialog .el-dialog__footer) {
  background-color: #2d2d2d !important;
  border-top: 1px solid #404040 !important;
}

:deep(.forget-dialog .el-form-item__label) {
  color: #ffffff !important;
}

:deep(.forget-dialog .el-input__inner) {
  background-color: #404040 !important;
  border-color: #555555 !important;
  color: #ffffff !important;
}

:deep(.forget-dialog .el-input__inner::placeholder) {
  color: #999999 !important;
}

:deep(.forget-dialog .el-button--default) {
  background-color: #404040 !important;
  border-color: #555555 !important;
  color: #ffffff !important;
}

:deep(.forget-dialog .el-button--default:hover) {
  background-color: #555555 !important;
  border-color: #666666 !important;
}

:deep(.forget-dialog .el-button--primary) {
  background-color: #409EFF !important;
  border-color: #409EFF !important;
}

:deep(.forget-dialog .el-button--primary:hover) {
  background-color: #66b1ff !important;
  border-color: #66b1ff !important;
}

/* 确保弹窗遮罩层也是深色的 */
:deep(.el-overlay) {
  background-color: rgba(0, 0, 0, 0.7) !important;
}

.code-input-group {
  display: flex;
  gap: 10px;
}

.code-input-group .el-input {
  flex: 1;
}

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

  .code-input-group {
    flex-direction: column;
  }
}

/* 去除 el-dialog__wrapper 的白色底色 */
:deep(.el-dialog__wrapper) {
  background-color: transparent !important;
}

/* 如果 wrapper 有 padding，移除以避免白色边框 */
:deep(.el-dialog__wrapper) {
  padding: 0 !important;
}
</style>