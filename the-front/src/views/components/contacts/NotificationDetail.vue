<template>
  <div class="notification-detail">
    <div v-if="notifications.length" class="notification-list">
      <el-timeline>
        <el-timeline-item
          v-for="(notification, index) in notifications"
          :key="index"
          :timestamp="formatTime(notification.created_at || notification.timestamp)"
          :type="notification.status === 'requested' ? 'warning' : notification.status === 'accepted' ? 'success' : 'info'"
        >
          <el-card class="notification-card">
            <div class="notification-content">
              <h4>{{ getTitle(notification) }}</h4>
              <p>{{ getContent(notification) }}</p>
              
              <div v-if="notification.status === 'requested'" class="action-buttons">
                <el-button type="success" size="small" @click="handleAccept(notification)">接受</el-button>
                <el-button type="danger" size="small" @click="handleReject(notification)">拒绝</el-button>
              </div>
              
              <div v-else class="status-badge">
                <el-tag :type="notification.status === 'accepted' ? 'success' : 'danger'">
                  {{ notification.status === 'accepted' ? '已接受' : '已拒绝' }}
                </el-tag>
              </div>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>
    
    <div v-else class="empty-state">
      <el-empty description="暂无通知" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { ElMessage } from 'element-plus';
import axios from 'axios';

const props = defineProps({
  type: {
    type: String,
    required: true,
    validator: (value) => ['friend', 'group'].includes(value)
  },
  notifications: {
    type: Array,
    default: () => []
  }
});

// 获取当前用户ID
const getCurrentUserId = () => {
  try {
    const authData = localStorage.getItem('authorize');
    if (authData) {
      const parsedAuth = JSON.parse(authData);
      return parsedAuth?.id || '';
    }
  } catch (e) {
    console.error("Error parsing auth data:", e);
  }
  return '';
};

const title = computed(() => {
  return props.type === 'friend' ? '好友通知' : '群组通知';
});

const formatTime = (timestamp) => {
  if (!timestamp) return '';
  
  try {
    const date = new Date(timestamp);
    return date.toLocaleString();
  } catch (e) {
    return '';
  }
};

const getTitle = (notification) => {
  if (props.type === 'friend') {
    if (notification.firstUsername) {
      return `来自 ${notification.firstUsername} 的好友请求`;
    }
    return `来自 ${notification.sender || '用户'} 的好友请求`;
  } else {
    return `来自 ${notification.groupName || '群组'} 的邀请`;
  }
};

const getContent = (notification) => {
  if (props.type === 'friend') {
    return notification.content || '请求添加您为好友';
  } else {
    return notification.content || '邀请您加入群组';
  }
};

const handleAccept = async (notification) => {
  if (props.type === 'friend') {
    try {
      // 获取授权令牌
      const authData = localStorage.getItem('authorize');
      if (!authData) {
        ElMessage.error('用户未登录');
        return;
      }
      
      const parsedAuth = JSON.parse(authData);
      const jwt = parsedAuth?.token;
      
      if (!jwt) {
        ElMessage.error('无效的认证信息');
        return;
      }
      
      // 调用接受好友请求API，根据FriendsResponse结构获取requestId
      const requestId = notification.id || notification.firstUserId;
      
      const response = await axios.post('/api/friend/accept', 
        { requestId: requestId },
        { headers: { 'Authorization': 'Bearer ' + jwt } }
      );
      
      if (response.data.code === 200) {
        ElMessage.success('已接受好友请求');
        notification.status = 'accepted';
      } else {
        ElMessage.error(response.data.message || '操作失败');
      }
    } catch (error) {
      console.error('接受好友请求失败:', error);
      ElMessage.error('接受请求时发生错误');
    }
  } else {
    // 处理群组邀请的接受
    ElMessage.success('已接受群组邀请');
    notification.status = 'accepted';
  }
};

const handleReject = async (notification) => {
  if (props.type === 'friend') {
    try {
      // 获取授权令牌
      const authData = localStorage.getItem('authorize');
      if (!authData) {
        ElMessage.error('用户未登录');
        return;
      }
      
      const parsedAuth = JSON.parse(authData);
      const jwt = parsedAuth?.token;
      
      if (!jwt) {
        ElMessage.error('无效的认证信息');
        return;
      }
      
      // 调用拒绝好友请求API，根据FriendsResponse结构获取requestId
      const requestId = notification.id || notification.firstUserId;
      
      const response = await axios.post('/api/friend/reject', 
        { requestId: requestId },
        { headers: { 'Authorization': 'Bearer ' + jwt } }
      );
      
      if (response.data.code === 200) {
        ElMessage.warning('已拒绝好友请求');
        notification.status = 'rejected';
      } else {
        ElMessage.error(response.data.message || '操作失败');
      }
    } catch (error) {
      console.error('拒绝好友请求失败:', error);
      ElMessage.error('拒绝请求时发生错误');
    }
  } else {
    // 处理群组邀请的拒绝
    ElMessage.warning('已拒绝群组邀请');
    notification.status = 'rejected';
  }
};
</script>

<style scoped>
.notification-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--primary-color);
}

.notification-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-secondary);
}

.notification-card {
  margin-bottom: 16px;
  border: none !important;
  background-color: var(--secondary-color) !important;
  color: var(--text-color) !important;
  box-shadow: var(--shadow-sm) !important;
  transition: all var(--transition-fast);
}

.notification-card:hover {
  box-shadow: var(--shadow-md) !important;
  transform: translateY(-2px);
}

.notification-content {
  padding: 8px 5px;
}

.notification-content h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  color: var(--text-color);
  font-weight: 600;
}

.notification-content p {
  margin: 0 0 15px 0;
  color: var(--text-secondary);
  line-height: 1.5;
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.status-badge {
  margin-top: 12px;
}

/* Timeline custom styles */
:deep(.el-timeline) {
  padding-left: 10px;
}

:deep(.el-timeline-item__tail) {
  border-left-color: var(--border-color) !important;
}

:deep(.el-timeline-item__node) {
  background-color: var(--accent-color) !important;
}

:deep(.el-timeline-item__node--warning) {
  background-color: var(--warning-color) !important;
}

:deep(.el-timeline-item__node--success) {
  background-color: var(--success-color) !important;
}

:deep(.el-timeline-item__node--danger) {
  background-color: var(--error-color) !important;
}

:deep(.el-timeline-item__timestamp) {
  color: var(--text-secondary) !important;
  padding-left: 12px;
}

:deep(.el-card__body) {
  padding: 16px;
  color: var(--text-color);
  background-color: var(--secondary-color) !important;
}

:deep(.el-tag) {
  border-radius: var(--border-radius-sm);
  padding: 4px 10px;
  font-weight: 500;
}

:deep(.el-tag--success) {
  background-color: rgba(46, 204, 113, 0.15) !important;
  border: 1px solid var(--success-color) !important;
  color: var(--success-color) !important;
}

:deep(.el-tag--danger) {
  background-color: rgba(231, 76, 60, 0.15) !important;
  border: 1px solid var(--error-color) !important;
  color: var(--error-color) !important;
}
</style> 