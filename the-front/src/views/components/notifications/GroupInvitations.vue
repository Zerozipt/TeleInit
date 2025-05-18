<template>
  <div class="invitations-container">
    <el-empty v-if="!invitations || invitations.length === 0" description="没有群组邀请" />
    
    <el-card v-else v-for="invitation in processedInvitations" :key="invitation.id" class="invitation-card">
      <div class="invitation-header">
        <strong>{{ invitation.inviterName }}</strong> 邀请你加入群组 <strong>{{ invitation.groupName }}</strong>
      </div>
      
      <div class="invitation-content">
        <p>群组ID: {{ invitation.groupId }}</p>
        <p>邀请时间: {{ formatDate(invitation.createdAt) }}</p>
      </div>
      
      <div class="invitation-actions" v-if="getInvitationStatus(invitation) === 'pending'">
        <el-button 
          type="primary" 
          size="small" 
          @click="handleAccept(invitation.id)"
          :loading="actionLoading && activeInvitationId === invitation.id"
        >
          接受
        </el-button>
        <el-button 
          type="danger" 
          size="small" 
          @click="handleReject(invitation.id)"
          :loading="actionLoading && activeInvitationId === invitation.id"
        >
          拒绝
        </el-button>
      </div>
      
      <div class="invitation-status" v-else>
        <el-tag 
          :type="getInvitationStatus(invitation) === 'accepted' ? 'success' : 'info'"
        >
          {{ getStatusText(invitation) }}
        </el-tag>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { respondToGroupInvitation } from '@/api/groupApi';
import { ElMessage } from 'element-plus';
import stompClientInstance from '@/net/websocket';

const actionLoading = ref(false);
const activeInvitationId = ref(null);

// 直接从WebSocket实例获取群组邀请
const invitations = computed(() => stompClientInstance.groupInvitations.value || []);

// 处理邀请数据，确保状态字段统一
const processedInvitations = computed(() => {
  return invitations.value.map(invitation => {
    // 创建新对象，避免修改原对象
    return {
      ...invitation,
      // 确保有状态字段
      processedStatus: getInvitationStatus(invitation)
    };
  });
});

// 获取邀请状态
const getInvitationStatus = (invitation) => {
  // 后端可能返回不同格式的状态值
  const status = invitation.status?.toLowerCase?.() || '';
  
  // 标准化状态值
  if (status === '' || status === 'pending' || status === 'requested') {
    return 'pending';
  } else if (status === 'accepted' || status === 'joined') {
    return 'accepted';
  } else if (status === 'rejected' || status === 'declined' || status === 'refused' || status === 'denied') {
    return 'rejected';
  }
  
  // 默认为待处理状态，确保能看到操作按钮
  return 'pending';
};

// 获取状态显示文本
const getStatusText = (invitation) => {
  const status = getInvitationStatus(invitation);
  
  if (status === 'accepted') {
    return '已接受';
  } else if (status === 'rejected') {
    return '已拒绝';
  } else if (status === 'pending') {
    return '待处理';
  }
  
  return '未知状态';
};

// 处理接受邀请
const handleAccept = async (invitationId) => {
  actionLoading.value = true;
  activeInvitationId.value = invitationId;
  
  try {
    const success = await respondToGroupInvitation(invitationId, 'accept');
    if (success) {
      // 刷新群组列表
      if (stompClientInstance && typeof stompClientInstance.refreshGroups === 'function') {
        await stompClientInstance.refreshGroups();
      }
      
      // 刷新群组邀请列表
      if (stompClientInstance && typeof stompClientInstance.refreshGroupInvitations === 'function') {
        await stompClientInstance.refreshGroupInvitations();
      }
      
      ElMessage.success('已接受邀请');
    }
  } catch (error) {
    console.error('接受邀请失败:', error);
    ElMessage.error('接受邀请失败');
  } finally {
    actionLoading.value = false;
    activeInvitationId.value = null;
  }
};

// 处理拒绝邀请
const handleReject = async (invitationId) => {
  actionLoading.value = true;
  activeInvitationId.value = invitationId;
  
  try {
    const success = await respondToGroupInvitation(invitationId, 'reject');
    if (success) {
      // 刷新群组邀请列表
      if (stompClientInstance && typeof stompClientInstance.refreshGroupInvitations === 'function') {
        await stompClientInstance.refreshGroupInvitations();
      }
      
      ElMessage.success('已拒绝邀请');
    }
  } catch (error) {
    console.error('拒绝邀请失败:', error);
    ElMessage.error('拒绝邀请失败');
  } finally {
    actionLoading.value = false;
    activeInvitationId.value = null;
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

// 监听群组邀请更新事件
onMounted(() => {
  console.log('[GroupInvitations] 组件挂载，刷新群组邀请');
  // 组件挂载时刷新群组邀请列表
  if (stompClientInstance && typeof stompClientInstance.refreshGroupInvitations === 'function') {
    stompClientInstance.refreshGroupInvitations()
      .catch(error => console.error('刷新群组邀请失败:', error));
  }
  
  // 监听群组邀请更新事件
  stompClientInstance.on('onGroupInvitationsUpdated', (invitations) => {
    console.log('[GroupInvitations] 收到群组邀请更新:', invitations);
  });
  
  // 监听系统消息，检查是否有群组邀请相关消息
  stompClientInstance.on('onSystemMessage', (message) => {
    console.log('[GroupInvitations] 收到系统消息:', message);
    if (message.type === 'groupInvite') {
      // 收到群组邀请消息后刷新列表
      if (stompClientInstance && typeof stompClientInstance.refreshGroupInvitations === 'function') {
        stompClientInstance.refreshGroupInvitations()
          .catch(error => console.error('刷新群组邀请失败:', error));
      }
    }
  });
});

onBeforeUnmount(() => {
  // 组件卸载时移除事件监听
  stompClientInstance.off('onGroupInvitationsUpdated');
  stompClientInstance.off('onSystemMessage');
});
</script>

<style scoped>
.invitations-container {
  padding: 10px;
}

.invitation-card {
  margin-bottom: 15px;
  border-left: 3px solid #409EFF;
}

.invitation-header {
  font-size: 16px;
  margin-bottom: 10px;
}

.invitation-content {
  color: #666;
  font-size: 14px;
  margin-bottom: 10px;
}

.invitation-content p {
  margin: 5px 0;
}

.invitation-actions {
  display: flex;
  gap: 10px;
}

.invitation-status {
  margin-top: 10px;
}
</style> 