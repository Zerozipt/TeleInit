<template>
  <div class="settings-container">
    <div class="settings-header">
      <el-button @click="goBack" :icon="ArrowLeft" circle class="back-btn"></el-button>
      <h2>用户设置</h2>
    </div>

    <div class="settings-content">
      <!-- 个人信息卡片 -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <span>个人信息</span>
          </div>
        </template>
        
        <el-form :model="profileForm" ref="profileFormRef" label-width="100px">
          <el-form-item label="用户ID">
            <el-input :value="userProfile.id" disabled></el-input>
          </el-form-item>
          
          <el-form-item label="邮箱">
            <el-input :value="userProfile.email" disabled></el-input>
          </el-form-item>
          
          <el-form-item 
            label="用户名" 
            prop="username"
            :rules="usernameRules"
          >
            <div class="username-container">
              <el-input 
                v-model="profileForm.username" 
                :disabled="!editingUsername"
                placeholder="请输入新用户名"
              ></el-input>
              <el-button 
                v-if="!editingUsername" 
                @click="startEditUsername" 
                type="primary" 
                text
              >
                修改
              </el-button>
              <div v-else class="edit-actions">
                <el-button @click="saveUsername" type="primary" size="small" :loading="usernameLoading">
                  保存
                </el-button>
                <el-button @click="cancelEditUsername" size="small">取消</el-button>
              </div>
            </div>
          </el-form-item>
          
          <el-form-item label="注册时间">
            <el-input :value="formatDate(userProfile.registerTime)" disabled></el-input>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 密码修改卡片 -->
      <el-card class="password-card">
        <template #header>
          <div class="card-header">
            <span>密码修改</span>
          </div>
        </template>
        
        <el-form 
          :model="passwordForm" 
          ref="passwordFormRef" 
          :rules="passwordRules"
          label-width="100px"
        >
          <el-form-item label="邮箱" prop="email">
            <el-input 
              v-model="passwordForm.email" 
              type="email"
              placeholder="请输入邮箱"
            ></el-input>
          </el-form-item>
          
          <el-form-item label="验证码" prop="verificationCode">
            <div class="code-container">
              <el-input 
                v-model="passwordForm.verificationCode" 
                placeholder="请输入验证码"
              ></el-input>
              <el-button 
                @click="sendCode" 
                :disabled="codeCountdown > 0"
                :loading="sendingCode"
                type="primary"
              >
                {{ codeCountdown > 0 ? `${codeCountdown}s后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          
          <el-form-item label="新密码" prop="newPassword">
            <el-input 
              v-model="passwordForm.newPassword" 
              type="password" 
              show-password
              placeholder="请输入新密码"
            ></el-input>
          </el-form-item>
          
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input 
              v-model="passwordForm.confirmPassword" 
              type="password" 
              show-password
              placeholder="请确认新密码"
            ></el-input>
          </el-form-item>
          
          <el-form-item>
            <el-button 
              @click="updatePassword" 
              type="primary" 
              :loading="passwordLoading"
            >
              修改密码
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { 
  getUserProfile, 
  updateUsername, 
  sendPasswordChangeCode, 
  updatePassword as updatePasswordApi 
} from '@/api/settingsApi'

const router = useRouter()

// 响应式数据
const userProfile = reactive({
  id: '',
  username: '',
  email: '',
  role: '',
  registerTime: ''
})

const profileForm = reactive({
  username: ''
})

const passwordForm = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: ''
})

// 表单引用
const profileFormRef = ref()
const passwordFormRef = ref()

// 状态管理
const editingUsername = ref(false)
const usernameLoading = ref(false)
const passwordLoading = ref(false)
const sendingCode = ref(false)
const codeCountdown = ref(0)

// 表单验证规则
const usernameRules = [
  { required: true, message: '请输入用户名', trigger: 'blur' },
  { min: 2, max: 20, message: '用户名长度应在2-20个字符之间', trigger: 'blur' }
]

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
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
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// 方法
const goBack = () => {
  router.go(-1)
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  return new Date(dateString).toLocaleDateString('zh-CN')
}

const loadUserProfile = async () => {
  try {
    const response = await getUserProfile()
    console.log('getUserProfile response:', response)
    if (response.data && response.data.code === 200) {
      Object.assign(userProfile, response.data.data)
      profileForm.username = response.data.data.username
      passwordForm.email = response.data.data.email
    } else {
      ElMessage.error(response.data?.message || '获取用户信息失败')
    }
  } catch (error) {
    ElMessage.error('获取用户信息失败')
    console.error('Load user profile error:', error)
  }
}

const startEditUsername = () => {
  editingUsername.value = true
  profileForm.username = userProfile.username
}

const cancelEditUsername = () => {
  editingUsername.value = false
  profileForm.username = userProfile.username
}

const saveUsername = async () => {
  try {
    await profileFormRef.value.validateField('username')
    
    usernameLoading.value = true
    
    const response = await updateUsername(profileForm.username)
    if (response.data && response.data.code === 200) {
      userProfile.username = profileForm.username
      editingUsername.value = false
      ElMessage.success('用户名修改成功')
    } else {
      ElMessage.error(response.data?.message || '用户名修改失败')
    }
  } catch (error) {
    if (error !== false) { // 非表单验证错误
      ElMessage.error('用户名修改失败')
      console.error('Update username error:', error)
    }
  } finally {
    usernameLoading.value = false
  }
}

const sendCode = async () => {
  if (!passwordForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }

  try {
    sendingCode.value = true
    const response = await sendPasswordChangeCode(passwordForm.email)
    
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

const updatePassword = async () => {
  try {
    await passwordFormRef.value.validate()
    
    passwordLoading.value = true
    
    const response = await updatePasswordApi(
      passwordForm.email,
      passwordForm.verificationCode,
      passwordForm.newPassword
    )
    
    if (response.data && response.data.code === 200) {
      ElMessage.success('密码修改成功')
      // 清空表单
      passwordForm.verificationCode = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } else {
      ElMessage.error(response.data?.message || '密码修改失败')
    }
  } catch (error) {
    if (error !== false) { // 非表单验证错误
      ElMessage.error('密码修改失败')
      console.error('Update password error:', error)
    }
  } finally {
    passwordLoading.value = false
  }
}

// 生命周期
onMounted(() => {
  loadUserProfile()
})
</script>

<style scoped>
.settings-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.settings-header {
  display: flex;
  align-items: center;
  margin-bottom: 30px;
}

.back-btn {
  margin-right: 15px;
}

.settings-header h2 {
  margin: 0;
  color: #303133;
}

.settings-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-card, .password-card {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #303133;
}

.username-container {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.username-container .el-input {
  flex: 1;
}

.edit-actions {
  display: flex;
  gap: 5px;
}

.code-container {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-container .el-input {
  flex: 1;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .settings-container {
    padding: 10px;
  }
  
  .username-container {
    flex-direction: column;
    align-items: stretch;
  }
  
  .code-container {
    flex-direction: column;
  }
}
</style> 