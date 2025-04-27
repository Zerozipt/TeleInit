<template>
  <el-dialog
    :model-value="visible"
    title="群组操作"
    width="30%"
    @update:modelValue="closeDialog"
    @closed="resetState"
  >
    <el-tabs v-model="groupActionTab">
      <el-tab-pane label="创建群组" name="create">
        <el-form @submit.prevent="handleCreateGroup">
          <el-form-item label="群组名称">
             <el-input v-model="newGroupName" placeholder="输入群组名称" />
          </el-form-item>
          <div class="dialog-footer" style="margin-top: 10px">
            <el-button @click="closeDialog">取消</el-button>
            <el-button type="primary" @click="handleCreateGroup" :loading="isCreating">创建</el-button>
          </div>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="加入群组" name="join">
         <el-form @submit.prevent="handleJoinGroup">
            <el-form-item label="群组名称">
                <el-input v-model="joinGroupName" placeholder="输入群组名称" />
            </el-form-item>
            <div class="dialog-footer" style="margin-top: 10px">
                <el-button @click="closeDialog">取消</el-button>
                <el-button type="primary" @click="handleJoinGroup" :loading="isJoining">加入</el-button>
            </div>
         </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { createGroup as apiCreateGroup, joinGroup as apiJoinGroup } from '@/api/groupApi';
import { ElMessage } from 'element-plus';

const props = defineProps({
  visible: Boolean,
});

const emit = defineEmits(['update:visible', 'group-created', 'group-joined']);

const groupActionTab = ref('create');
const newGroupName = ref('');
const joinGroupName = ref('');
const isCreating = ref(false);
const isJoining = ref(false);

const closeDialog = () => {
  emit('update:visible', false);
};

const resetState = () => {
    groupActionTab.value = 'create';
    newGroupName.value = '';
    joinGroupName.value = '';
    isCreating.value = false;
    isJoining.value = false;
};

watch(() => props.visible, (newVal) => {
  if (!newVal) {
    // Resetting state when dialog is fully closed via @closed
  }
});

const handleCreateGroup = async () => {
  if (!newGroupName.value.trim()) {
      ElMessage.warning('请输入群组名称');
      return;
  }
  isCreating.value = true;
  try {
    const newGroup = await apiCreateGroup(newGroupName.value.trim());
    if (newGroup) {
      ElMessage.success(`群组 "${newGroup.name}" 创建成功`);
      emit('group-created', newGroup); // Pass the new group info back
      closeDialog();
    }
  } catch (error) {
    console.error('创建群组失败:', error);
    ElMessage.error(`创建群组失败: ${error.message || '请稍后重试'}`);
  } finally {
      isCreating.value = false;
  }
};

const handleJoinGroup = async () => {
  if (!joinGroupName.value.trim()) {
      ElMessage.warning('请输入要加入的群组名称');
      return;
  }
  isJoining.value = true;
  try {
    // Assuming apiJoinGroup returns the joined group info or confirms success
    const result = await apiJoinGroup(joinGroupName.value.trim());
    if (result) { // Adapt this check based on actual API response
      ElMessage.success(`成功加入群组 "${joinGroupName.value}"`);
      // It's better to emit an event and let the parent handle the update
      // The specific group info might not be returned by joinGroup
      emit('group-joined', joinGroupName.value); 
      closeDialog();
    }
    // Consider handling cases where the group doesn't exist or other errors
  } catch (error) {
    console.error('加入群组失败:', error);
    ElMessage.error(`加入群组失败: ${error.message || '请检查群组名称或网络'}`);
  } finally {
      isJoining.value = false;
  }
};

</script>

<style scoped>
/* Use el-form for better layout and potential validation */
.el-form-item {
    margin-bottom: 15px; /* Add some space between items */
}

.dialog-footer {
    display: flex;
    justify-content: flex-end;
    /* margin-top: 20px; Provided inline for now */
}

/* Ensure tabs content has some padding if needed */
:deep(.el-tabs__content) {
    padding-top: 15px;
}
</style> 