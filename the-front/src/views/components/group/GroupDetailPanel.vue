<template>
  <div class="group-detail-panel">
    <el-card v-if="loading" class="loading-card">
      <el-skeleton animated :rows="4" />
    </el-card>

    <template v-else-if="groupDetail">
      <!-- 群组基本信息 -->
      <div class="group-header">
        <div class="group-avatar">
          <el-avatar :size="50" :src="null" style="background-color: #409EFF">
            {{ groupDetail.name?.charAt(0).toUpperCase() || 'G' }}
          </el-avatar>
        </div>
        <div class="group-info">
          <h2 class="group-name">{{ groupDetail.name }}</h2>
          <el-tag size="small">{{ groupDetail.memberCount }}人</el-tag>
        </div>
      </div>

      <!-- 基本信息卡片 -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
          </div>
        </template>
        <div class="info-content">
          <p><strong>创建者:</strong> {{ groupDetail.creatorName }}</p>
          <p><strong>创建时间:</strong> {{ formatDate(getCreationTime()) }}</p>
          <p><strong>群组ID:</strong> {{ groupDetail.groupId }}</p>
        </div>
      </el-card>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <el-button type="primary" @click="$emit('invite-friend')" icon="Plus">
          邀请好友
        </el-button>
      </div>

      <!-- 成员列表卡片 -->
      <el-card class="members-card">
        <template #header>
          <div class="card-header">
            <span>成员列表</span>
          </div>
        </template>
        
        <div class="members-list">
          <div v-for="member in groupDetail.members" :key="member.userId" class="member-item">
            <el-avatar :size="40" :src="member.avatar">
              {{ member.username?.charAt(0).toUpperCase() || 'U' }}
            </el-avatar>
            <div class="member-info">
              <div class="member-name">
                {{ member.username }}
                <el-tag 
                  v-if="member.role" 
                  size="small" 
                  :type="getRoleTagType(member.role)"
                  class="role-tag"
                >
                  {{ formatRole(member.role) }}
                </el-tag>
              </div>
              <div class="member-join-time">
                加入于: {{ formatDate(member.joinedAt) }}
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 退出群组按钮 -->
      <div class="exit-button-container">
        <el-button type="danger" @click="handleExitGroup" style="width: 100%; margin-top: 16px;">
          退出群组
        </el-button>
      </div>
    </template>

    <div v-else class="error-container">
      <el-empty description="无法加载群组信息" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getGroupDetail, exitGroup } from '@/api/groupApi';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';

const props = defineProps({
  groupId: {
    type: String,
    required: true
  }
});

const emit = defineEmits(['invite-friend', 'exit-group']);

// 状态
const loading = ref(true);
const groupDetail = ref(null);

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

// 获取群组创建时间（使用创建者的加入时间作为群组创建时间）
const getCreationTime = () => {
  if (!groupDetail.value || !groupDetail.value.members) return null;
  
  // 查找创建者成员
  const creator = groupDetail.value.members.find(
    member => member.userId === groupDetail.value.creatorId
  );
  
  // 返回创建者的加入时间作为群组创建时间
  return creator ? creator.joinedAt : groupDetail.value.createAt;
};

// 监听群组ID变化
watch(() => props.groupId, (newGroupId) => {
  if (newGroupId) {
    loadGroupDetail();
  }
});

// 生命周期钩子
onMounted(() => {
  if (props.groupId) {
    loadGroupDetail();
  }
});

// 退出群组处理
const handleExitGroup = async () => {
  try {
    const success = await exitGroup(props.groupId);
    if (success) {
      ElMessage.success('已退出群组');
      emit('exit-group');
    }
  } catch (error) {
    console.error('退出群组失败:', error);
  }
};
</script>

<style scoped>
.group-detail-panel {
  padding: 15px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 15px;
  overflow-y: auto;
  overflow-x: hidden; /* 防止内容水平溢出 */
  box-sizing: border-box; /* 确保padding不会导致溢出 */
  width: 100%; /* 确保面板宽度占满父容器 */
}

.loading-card, .error-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%; /* 确保宽度占满 */
}

/* 确保卡片内容自适应 */
.info-card, .members-card {
  width: 100%;
  margin-bottom: 0;
  word-break: break-word; /* 确保长文本自动换行 */
  box-sizing: border-box; /* 确保边距不会导致溢出 */
}

/* 调整群组头像大小，防止挤压 */
.group-header {
  display: flex;
  align-items: center;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--border-color);
  flex-wrap: wrap; /* 允许在空间不足时换行 */
}

.group-info {
  margin-left: 15px;
}

.group-info h2 {
  margin: 0 0 5px 0;
  font-size: 16px; /* 减小字体大小 */
  word-break: break-word; /* 确保长文字自动换行 */
  max-width: 100%; /* 确保文字不超出容器 */
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}

.info-content {
  font-size: 14px;
}

.info-content p {
  margin: 8px 0;
}

.action-buttons {
  display: flex;
  justify-content: center;
  margin: 8px 0; /* 减小上下外边距 */
}

/* 调整群组成员列表最大高度，保证在小屏幕上也能正常显示 */
.members-list {
  max-height: 40vh; /* 使用视口高度单位，更加灵活 */
  overflow-y: auto;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 8px 0; /* 减小上下间距 */
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.member-item:last-child {
  border-bottom: none;
}

.member-info {
  margin-left: 10px;
  flex: 1;
}

.member-name {
  font-weight: 500;
  display: flex;
  align-items: center;
}

.role-tag {
  margin-left: 5px;
}

.member-join-time {
  font-size: 12px;
  color: #909399;
  margin-top: 3px;
}

.group-name {
  margin: 0 0 5px 0;
  font-size: 16px; /* 减小字体大小 */
  word-break: break-word; /* 确保长文字自动换行 */
  max-width: 100%; /* 确保文字不超出容器 */
}
</style> 