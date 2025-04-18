<template>
  <div class="group-management">
    <!-- 创建群聊部分 -->
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span><i class="el-icon-plus"></i> 创建新群聊</span>
        </div>
      </template>
      <el-form :model="createForm" ref="createFormRef" @submit.prevent="handleCreateGroup">
        <el-form-item label="群聊名称" prop="name" :rules="[{ required: true, message: '请输入群聊名称', trigger: 'blur' }]">
          <el-input v-model="createForm.name" placeholder="输入群聊名称..." clearable></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="createLoading">创建群聊</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 加入群聊部分 -->
    <el-card class="box-card" shadow="never" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span><i class="el-icon-user"></i> 加入现有群聊</span>
        </div>
      </template>
      <el-form :model="joinForm" ref="joinFormRef" @submit.prevent="handleJoinGroup">
        <el-form-item label="群聊 名称" prop="groupName" :rules="[{ required: true, message: '请输入要加入的群聊名称', trigger: 'blur' }]">
          <el-input v-model="joinForm.groupName" placeholder="输入群聊名称..." clearable></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="success" native-type="submit" :loading="joinLoading">加入群聊</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { createGroup, joinGroup } from '@/api/groupApi'; // 导入 API 函数
import { ElForm, ElMessage } from 'element-plus';

// 定义 emit，用于通知父组件操作结果
const emit = defineEmits(['group-created', 'group-joined']);

// 创建群聊表单
const createFormRef = ref(null);
const createForm = reactive({
  name: '',
});
const createLoading = ref(false);

// 加入群聊表单
const joinFormRef = ref(null);
const joinForm = reactive({
  groupName: '',
});
const joinLoading = ref(false);

// 处理创建群聊
const handleCreateGroup = async () => {
  if (!createFormRef.value) return;
  await createFormRef.value.validate(async (valid) => {
    if (valid) {
      createLoading.value = true;
      try {
        const newGroup = await createGroup(createForm.name);
        if (newGroup) {
          // 触发事件，将新群组信息传递给父组件
          emit('group-created', newGroup);
          createForm.name = ''; // 清空输入框
          createFormRef.value.resetFields(); // 重置表单验证状态
        }
      } finally {
        createLoading.value = false;
      }
    } else {
      ElMessage.warning('请检查输入');
      return false;
    }
  });
};

// 处理加入群聊
const handleJoinGroup = async () => {
  if (!joinFormRef.value) return;
  await joinFormRef.value.validate(async (valid) => {
    if (valid) {
      joinLoading.value = true;
      try {
        // joinGroup 成功时只返回成员信息，我们需要 groupId 通知父组件
        const joinResult = await joinGroup(joinForm.groupName);
        if (joinResult) {
          // 触发事件，传递加入的 groupId
          // 后端成功加入后，最好能返回完整的群组信息，方便前端更新
          // 这里暂时只传递 groupId，父组件需要重新获取群组信息
          emit('group-joined', joinForm.groupName);
          joinForm.groupName = ''; // 清空输入框
          joinFormRef.value.resetFields(); // 重置表单验证状态
        }
      } finally {
        joinLoading.value = false;
      }
    } else {
      ElMessage.warning('请检查输入');
      return false;
    }
  });
};
</script>

<style scoped>
.group-management {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header span {
  font-weight: bold;
}
.card-header i {
  margin-right: 5px;
}
.el-form-item {
  margin-bottom: 18px; /* 调整表单项间距 */
}
</style>