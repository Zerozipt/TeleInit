<template>
  <div class="group-detail-container">
    <el-card v-if="loading" class="loading-card">
      <el-skeleton animated :rows="6" />
    </el-card>

    <template v-else-if="groupDetail">
      <!-- 群组基本信息 -->
      <el-card class="group-info-card">
        <template #header>
          <div class="card-header">
            <h2>{{ groupDetail.name }}</h2>
            <el-tag>{{ groupDetail.memberCount }}人</el-tag>
          </div>
        </template>
        <div class="group-info">
          <p><strong>创建者:</strong> {{ groupDetail.creatorName }}</p>
          <p><strong>创建时间:</strong> {{ formatDate(groupDetail.createAt) }}</p>
          <p><strong>群组ID:</strong> {{ groupDetail.groupId }}</p>
        </div>
      </el-card>

      <!-- 群组成员列表 -->
      <el-card class="member-list-card">
        <template #header>
          <div class="card-header">
            <h3>成员列表</h3>
            <div>
              <el-button type="primary" size="small" @click="showInviteDialog">
                邀请好友
              </el-button>
            </div>
          </div>
        </template>

        <!-- 成员列表 -->
        <el-table :data="groupDetail.members" stripe style="width: 100%">
          <el-table-column label="头像" width="80">
            <template #default="{ row }">
              <el-avatar :src="row.avatar || defaultAvatar" :alt="row.username"></el-avatar>
            </template>
          </el-table-column>
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="role" label="角色" width="120">
            <template #default="{ row }">
              <el-tag :type="getRoleTagType(row.role)">{{ formatRole(row.role) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="加入时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.joinedAt) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 未找到群组时的提示 -->
    <el-empty v-else description="未找到群组信息" />

    <!-- 邀请好友对话框 -->
    <el-dialog
      v-model="inviteDialogVisible"
      title="邀请好友加入群组"
      width="500px"
    >
      <el-form :model="inviteForm" @submit.prevent="handleInvite">
        <el-alert
          title="注意: 只能邀请已是你好友的用户"
          type="warning"
          :closable="false"
          style="margin-bottom: 15px"
        />
        <el-form-item label="选择好友" prop="friendId">
          <el-select v-model="inviteForm.friendId" placeholder="选择好友" style="width: 100%">
            <el-option 
              v-for="friend in friendsList" 
              :key="friend.friendId" 
              :label="friend.friendName" 
              :value="friend.friendId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="inviteLoading">发送邀请</el-button>
          <el-button @click="inviteDialogVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { getGroupDetail, inviteUserToGroup } from '@/api/groupApi';
import { ElMessage } from 'element-plus';
import stompClientInstance from '@/net/websocket';

const props = defineProps({
  groupId: {
    type: String,
    required: true
  }
});

// 状态
const loading = ref(true);
const groupDetail = ref(null);
const inviteDialogVisible = ref(false);
const inviteForm = ref({ friendId: null });
const inviteLoading = ref(false);
const defaultAvatar = '/default-avatar.jpg';

// 计算属性 - 从WebSocket实例获取好友列表
const friendsList = computed(() => {
  const friends = stompClientInstance.friends.value || [];
  // 排除已经在群组中的好友
  if (groupDetail.value && groupDetail.value.members) {
    const memberIds = groupDetail.value.members.map(m => m.userId);
    return friends.filter(f => !memberIds.includes(f.friendId));
  }
  return friends;
});

// 加载群组详情
const loadGroupDetail = async () => {
  loading.value = true;
  try {
    const detail = await getGroupDetail(props.groupId);
    if (detail) {
      groupDetail.value = detail;
      console.log('群组详情加载成功:', detail);
    } else {
      ElMessage.error('加载群组详情失败');
    }
  } catch (error) {
    console.error('加载群组详情出错:', error);
    ElMessage.error('加载群组详情时发生错误');
  } finally {
    loading.value = false;
  }
};

// 格式化时间
const formatDate = (dateString) => {
  if (!dateString) return '未知';
  
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 格式化角色
const formatRole = (role) => {
  if (!role) return '成员';
  
  const roleMap = {
    'CREATOR': '群主',
    'ADMIN': '管理员',
    'MEMBER': '成员'
  };
  
  return roleMap[role] || role;
};

// 获取角色标签类型
const getRoleTagType = (role) => {
  if (!role) return '';
  
  const typeMap = {
    'CREATOR': 'danger',
    'ADMIN': 'warning',
    'MEMBER': 'info'
  };
  
  return typeMap[role] || '';
};

// 显示邀请对话框
const showInviteDialog = () => {
  inviteForm.value = { friendId: null };
  inviteDialogVisible.value = true;
};

// 处理邀请
const handleInvite = async () => {
  if (!inviteForm.value.friendId) {
    ElMessage.warning('请选择要邀请的好友');
    return;
  }
  
  inviteLoading.value = true;
  try {
    const success = await inviteUserToGroup(props.groupId, inviteForm.value.friendId);
    if (success) {
      inviteDialogVisible.value = false;
    }
  } catch (error) {
    console.error('邀请好友失败:', error);
  } finally {
    inviteLoading.value = false;
  }
};

// 生命周期钩子
onMounted(() => {
  loadGroupDetail();
});
</script>

<style scoped>
.group-detail-container {
  padding: 20px;
}

.group-info-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h2, .card-header h3 {
  margin: 0;
}

.group-info {
  padding: 10px 0;
}

.member-list-card {
  margin-top: 20px;
}

.loading-card {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style> 