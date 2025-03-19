<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { askVerifyCode ,resetPassword} from '@/net'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const resetFormRef = ref()
const animateForm = ref(false)

const form = reactive({
  email: '',
  code: '',
  password: '',
  password_repeat: ''
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { min: 6, max: 6, message: '验证码长度不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应为6-20个字符', trigger: 'blur' }
  ],
  password_repeat: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
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
  ]
}

onMounted(() => {
  setTimeout(() => animateForm.value = true, 100)
})

const handleResetPassword = () => {
  resetFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await resetPassword(
          form.email,
          form.code,
          form.password,
          () => {
            ElMessage.success('密码重置成功！请使用新密码登录')
            router.push('/login')
          },
          (message) => {
            ElMessage.error(message)
          }
        )
      } catch (error) {
        ElMessage.error('密码重置过程中发生错误')
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
  askVerifyCode('reset', form.email, (message) => {
    console.log(message)
    if(message === "success"){
      ElMessage.success('验证码已发送至 ' + form.email)
    }else{
      ElMessage.error(message)
    }
  })
}

const goToLogin = () => router.push({ name: 'welcome-login' })
</script>

<template>
  <div class="forget-container">
    <div class="background"></div>
    
    <div class="forget-panel" :class="{ 'animate': animateForm }">
      <div class="forget-side">
        <div class="logo-icon"><i class="el-icon-key"></i></div>
        <h2>找回密码</h2>
        <p>重置您的账号密码，保障账号安全</p>
        <div class="illustration"></div>
      </div>

      <div class="forget-form-container">
        <h2>重置密码</h2>
        <p>请填写以下信息完成密码重置</p>

        <el-form
            ref="resetFormRef"
            :model="form"
            :rules="rules"
            label-position="top"
            class="forget-form">

          <el-form-item label="邮箱" prop="email">
            <el-input
                v-model="form.email"
                placeholder="请输入邮箱">
              <template #prefix><i class="el-icon-message"></i></template>
            </el-input>
          </el-form-item>

          <el-form-item label="验证码" prop="code">
            <div class="verification-code">
              <el-input
                  v-model="form.code"
                  placeholder="请输入验证码">
                <template #prefix><i class="el-icon-key"></i></template>
              </el-input>
              <el-button type="primary" @click="sendVerificationCode">获取验证码</el-button>
            </div>
          </el-form-item>

          <el-form-item label="新密码" prop="password">
            <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入新密码"
                show-password>
              <template #prefix><i class="el-icon-lock"></i></template>
            </el-input>
          </el-form-item>

          <el-form-item label="确认新密码" prop="password_repeat">
            <el-input
                v-model="form.password_repeat"
                type="password"
                placeholder="请再次输入新密码"
                show-password>
              <template #prefix><i class="el-icon-lock"></i></template>
            </el-input>
          </el-form-item>
  
          <el-button
              type="primary"
              :loading="loading"
              class="forget-button"
              @click="handleResetPassword">
            {{ loading ? '提交中...' : '重置密码' }}
          </el-button>

          <div class="login-link">
            想起密码了？ <el-button type="text" @click="goToLogin">返回登录</el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.forget-container {
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

.forget-panel {
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

.forget-panel.animate {
  opacity: 1;
  transform: translateY(0);
}

.forget-side {
  flex: 1;
  background: linear-gradient(135deg, #ff9a3f 0%, #ff6b6b 100%);
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
  color: #ff9a3f;
}

.forget-side h2 {
  font-size: 28px;
  margin-bottom: 10px;
  font-weight: 600;
}

.forget-side p {
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

.forget-form-container {
  flex: 1;
  padding: 40px;
  display: flex;
  flex-direction: column;
}

.forget-form-container h2 {
  font-size: 22px;
  color: #32325d;
  font-weight: 600;
  margin-bottom: 8px;
  text-align: center;
}

.forget-form-container p {
  color: #8898aa;
  font-size: 14px;
  margin-bottom: 25px;
  text-align: center;
}

.forget-form {
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

.verification-code {
  display: flex;
  gap: 10px;
}

.verification-code .el-input {
  flex: 1;
}

.forget-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-top: 10px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: linear-gradient(135deg, #ff9a3f 0%, #ff6b6b 100%);
  border: none;
  transition: all 0.3s;
}

.forget-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 154, 63, 0.25);
}

.login-link {
  text-align: center;
  color: #8898aa;
  font-size: 14px;
  margin-bottom: 20px;
}

@media (max-width: 768px) {
  .forget-panel {
    flex-direction: column;
    min-height: auto;
  }
  
  .forget-side {
    padding: 30px 20px;
  }
  
  .illustration {
    height: 120px;
    margin: 10px 0;
  }
  
  .forget-form-container {
    padding: 30px 20px;
  }
  
  .verification-code {
    flex-direction: column;
    gap: 5px;
  }
}
</style>
