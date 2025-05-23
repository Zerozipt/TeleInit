<template>
  <div class="notification-detail">
    <div v-if="notifications.length" class="notification-list">
      <el-timeline>
        <el-timeline-item
          v-for="(notification, index) in notifications"
          :key="index"
          :timestamp="formatTime(notification.created_at || notification.timestamp)"
          :type="getStatusType(notification)"
        >
          <el-card class="notification-card">
            <div class="notification-content">
              <h4>{{ getTitle(notification) }}</h4>
              <p>{{ getContent(notification) }}</p>
              
              <div v-if="type === 'group' ? (notification.status === 'pending') : (notification.displayStatus === 'requested')" class="action-buttons">
                <el-button type="success" size="small" @click="handleAccept(notification)">接受</el-button>
                <el-button type="danger" size="small" @click="handleReject(notification)">拒绝</el-button>
              </div>
              
              <div v-else-if="notification.displayStatus === 'sent'" class="action-buttons">
                <el-button type="danger" size="small" @click="handleCancel(notification)">取消请求</el-button>
              </div>
              
              <div v-else-if="notification.displayStatus === 'accepting' || notification.status === 'accepting'" class="status-badge">
                <el-tag type="warning" effect="plain">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  处理中...
                </el-tag>
              </div>
              
              <div v-else-if="notification.displayStatus === 'rejecting' || notification.status === 'rejecting'" class="status-badge">
                <el-tag type="info" effect="plain">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  处理中...
                </el-tag>
              </div>
              
              <div v-else class="status-badge">
                <el-tag :type="getTagType(notification.displayStatus || notification.status)">
                  {{ getStatusText(notification.displayStatus || notification.status) }}
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
import { Loading } from '@element-plus/icons-vue';
import { acceptFriendRequest, rejectFriendRequest, cancelFriendRequest } from '@/api/friendApi';
import { respondToGroupInvitation } from '@/api/groupApi';
import stompClientInstance from '@/net/websocket';

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

// 定义事件
const emit = defineEmits([
  'friend-request-accepted',
  'friend-request-rejected',
  'friend-request-cancelled',
  'group-invitation-accepted',
  'group-invitation-rejected'
]);

// 获取当前用户ID - 从WebSocket实例获取
const getCurrentUserId = () => {
  // 直接从WebSocket实例获取当前用户ID
  const currentUserId = stompClientInstance.currentUserId.value;
  
  if (!currentUserId) {
    console.error("无法从WebSocket获取用户ID");
    return '';
  }
  
  return currentUserId;
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

// 返回状态类型，用于Timeline的type属性
const getStatusType = (notification) => {
  // 优先使用displayStatus，如果没有则回退到status
  const status = notification.displayStatus || notification.status;
  
  switch (status) {
    case 'requested': return 'warning';
    case 'pending': return 'warning';
    case 'accepted': return 'success';
    case 'rejected': 
    case 'deleted': return 'danger';
    case 'sent': return 'info';
    default: return 'info';
  }
};

// 返回状态文本
const getStatusText = (status) => {
  switch (status) {
    case 'accepted': return '已接受';
    case 'rejected': return '已拒绝';
    case 'deleted': return '已删除';
    case 'sent': return '已发送';
    case 'pending': return '待处理';
    case 'requested': return '等待接受';
    case 'accepting': return '处理中...';
    case 'rejecting': return '处理中...';
    default: return '未知状态';
  }
};

// 返回标签类型，用于el-tag的type属性
const getTagType = (status) => {
  switch (status) {
    case 'accepted': return 'success';
    case 'rejected': 
    case 'deleted': return 'danger';
    case 'sent': return 'info';
    case 'accepting': return 'warning';
    case 'rejecting': return 'info';
    default: return 'info';
  }
};

const getTitle = (notification) => {
  if (props.type === 'friend') {
    const currentUserIdStr = getCurrentUserId(); // 确保获取的是字符串ID

    // 检查 notification 对象和其属性是否存在
    if (!notification || !notification.displayStatus) {
      return '好友请求'; // 返回一个默认值或者错误提示
    }

    const isSender = notification.firstUserId === currentUserIdStr;
    const otherUserName = isSender ? notification.secondUsername : notification.firstUsername;
    const defaultOtherUserName = isSender ? '用户' : (notification.sender || '用户');

    switch (notification.displayStatus) {
      case 'sent': // 当前用户发送的，等待对方回应
        return `发给 ${otherUserName || defaultOtherUserName} 的好友请求`;
      case 'requested': // 当前用户收到的，等待自己回应
        return `来自 ${otherUserName || defaultOtherUserName} 的好友请求`;
      case 'accepted':
        if (isSender) {
          return `发给 ${otherUserName || defaultOtherUserName} 的请求已被接受`;
        } else {
          return `${otherUserName || defaultOtherUserName} 已接受您的好友请求`;
        }
      case 'rejected':
        if (isSender) {
          return `发给 ${otherUserName || defaultOtherUserName} 的请求已被拒绝`;
        } else {
          // 如果是接收者，可能是自己拒绝了，也可能是对方取消了
          // ChatServiceImpl 中 rejectFriendRequestByUsers 和 cancelFriendRequest 都会将 DTO status 置为 rejected
          // websocket.js 的 _processFriendRequests 会将 DTO status 'rejected' 或 'deleted' 都映射为 displayStatus 'rejected'
          return `${otherUserName || defaultOtherUserName} 的好友请求已处理`; // 使用更通用的描述
        }
      default:
        return `好友请求状态: ${notification.displayStatus}`;
    }
  } else {
    return `来自 ${notification.groupName || '群组'} 的邀请`;
  }
};

const getContent = (notification) => {
  if (props.type === 'friend') {
    const currentUserIdStr = getCurrentUserId();

    if (!notification || !notification.displayStatus) {
      return '无法加载内容';
    }

    const isSender = notification.firstUserId === currentUserIdStr;

    switch (notification.displayStatus) {
      case 'sent':
        return notification.content || '等待对方回应您的好友请求。';
      case 'requested':
        return notification.content || '请求添加您为好友，请及时处理。';
      case 'accepted':
        if (isSender) {
          return notification.content || `您与 ${notification.secondUsername || '对方'} 已成为好友。`;
        } else {
          return notification.content || `您已接受 ${notification.firstUsername || '对方'} 的好友请求。`;
        }
      case 'rejected':
        if (isSender) {
          return notification.content || `您发给 ${notification.secondUsername || '对方'} 的好友请求已被拒绝或取消。`;
        } else {
          // 对于接收者，"rejected"可能是自己拒绝的，也可能是对方取消了请求
          return notification.content || `您已处理来自 ${notification.firstUsername || '对方'} 的好友请求。`;
        }
      default:
        return notification.content || `状态: ${notification.displayStatus}`;
    }
  } else {
    return notification.content || '邀请您加入群组';
  }
};

const handleAccept = async (notification) => {
  if (props.type === 'friend') {
    try {
      // 乐观更新：立即更新UI状态
      const originalStatus = notification.displayStatus;
      const originalStatusBackup = notification.status;
      
      notification.displayStatus = 'accepting';  // 临时状态：处理中
      notification.status = 'accepting';
      
      // 获取发送者ID和接收者ID
      const currentUserId = getCurrentUserId();
      // 确保是整数类型，使用parseInt进行转换
      const senderId = parseInt(notification.theFirstUserId || notification.firstUserId, 10);
      const receiverId = parseInt(currentUserId, 10);
      
      if (isNaN(senderId) || isNaN(receiverId)) {
        // 恢复原状态
        notification.displayStatus = originalStatus;
        notification.status = originalStatusBackup;
        ElMessage.error('无效的用户ID格式');
        return;
      }
      
      console.log('接受好友请求参数:', { senderId, receiverId });
      
      // 乐观更新：立即添加到好友列表
      const newFriend = {
        friendId: senderId.toString(),
        friendName: notification.firstUsername,
        isOnline: false, // 默认离线，后续WebSocket会更新
        tempAccepted: true // 标记为临时接受状态
      };
      
      // 添加到WebSocket实例的好友列表
      if (window.stompClientInstance && window.stompClientInstance.friends) {
        window.stompClientInstance.friends.value = window.stompClientInstance.friends.value || [];
        window.stompClientInstance.friends.value.unshift(newFriend);
      }
      
      // 使用friendApi中的方法接受好友请求
      const result = await acceptFriendRequest(senderId, receiverId);
      
      if (result) {
        // 成功：确认更新状态
        notification.status = 'accepted';
        notification.displayStatus = 'accepted';
        ElMessage.success('已接受好友请求');
        
        // 确认好友关系已建立，移除临时标记
        if (window.stompClientInstance && window.stompClientInstance.friends.value) {
          const friendIndex = window.stompClientInstance.friends.value.findIndex(f => 
            f.friendId === senderId.toString() && f.tempAccepted
          );
          if (friendIndex !== -1) {
            delete window.stompClientInstance.friends.value[friendIndex].tempAccepted;
          }
        }
        
        // 触发接受事件
        emit('friend-request-accepted', notification);
        
        // 设置超时检查机制
        setTimeout(() => {
          // 检查是否收到WebSocket确认
          if (window.stompClientInstance && window.stompClientInstance.friends.value) {
            const confirmed = window.stompClientInstance.friends.value.some(f => 
              f.friendId === senderId.toString() && !f.tempAccepted
            );
            
            if (!confirmed) {
              console.warn('好友关系可能未完全同步到服务器');
              ElMessage.warning('好友关系同步中，请稍候刷新');
            }
          }
        }, 8000);
        
      } else {
        // API返回失败：恢复原状态
        notification.displayStatus = originalStatus;
        notification.status = originalStatusBackup;
        
        // 移除乐观添加的好友
        if (window.stompClientInstance && window.stompClientInstance.friends.value) {
          window.stompClientInstance.friends.value = window.stompClientInstance.friends.value.filter(f => 
            !(f.friendId === senderId.toString() && f.tempAccepted)
          );
        }
        
        ElMessage.error('操作失败');
      }
    } catch (error) {
      console.error('接受好友请求失败:', error);
      
      // 错误：恢复原状态
      notification.displayStatus = originalStatus;
      notification.status = originalStatusBackup;
      
      // 移除乐观添加的好友
      if (window.stompClientInstance && window.stompClientInstance.friends.value) {
        window.stompClientInstance.friends.value = window.stompClientInstance.friends.value.filter(f => 
          !(f.friendId === senderId.toString() && f.tempAccepted)
        );
      }
      
      ElMessage.error(error.message || '接受请求时发生错误');
    }
  } else {
    // 处理群组邀请的接受
    try {
      // 乐观更新群组邀请状态
      const originalStatus = notification.status;
      notification.status = 'accepting';
      
      const result = await respondToGroupInvitation(notification.id, 'accept');
      if (result) {
        notification.status = 'accepted';
        ElMessage.success('已接受群组邀请');
        
        // 乐观更新：立即添加到群组列表
        const newGroup = {
          groupId: notification.groupId,
          groupName: notification.groupName,
          tempJoined: true // 临时标记
        };
        
        if (window.stompClientInstance && window.stompClientInstance.groups) {
          window.stompClientInstance.groups.value = window.stompClientInstance.groups.value || [];
          window.stompClientInstance.groups.value.unshift(newGroup);
        }
        
        // 触发群组邀请接受事件
        emit('group-invitation-accepted', notification);
      } else {
        notification.status = originalStatus;
        ElMessage.error('接受群组邀请失败');
      }
    } catch (e) {
      console.error('接受群组邀请失败:', e);
      notification.status = originalStatus;
      ElMessage.error('接受群组邀请失败');
    }
  }
};

const handleReject = async (notification) => {
  if (props.type === 'friend') {
    try {
      // 乐观更新：立即更新UI状态
      const originalStatus = notification.displayStatus;
      const originalStatusBackup = notification.status;
      
      notification.displayStatus = 'rejecting';  // 临时状态：处理中
      notification.status = 'rejecting';
      
      // 获取发送者ID和接收者ID
      const currentUserId = getCurrentUserId();
      // 确保是整数类型，使用parseInt进行转换
      const senderId = parseInt(notification.theFirstUserId || notification.firstUserId, 10);
      const receiverId = parseInt(currentUserId, 10);
      
      if (isNaN(senderId) || isNaN(receiverId)) {
        // 恢复原状态
        notification.displayStatus = originalStatus;
        notification.status = originalStatusBackup;
        ElMessage.error('无效的用户ID格式');
        return;
      }
      
      console.log('拒绝好友请求参数:', { senderId, receiverId });
      
      // 使用friendApi中的方法拒绝好友请求
      const result = await rejectFriendRequest(senderId, receiverId);
      
      if (result) {
        // 成功：确认更新状态
        notification.status = 'deleted';
        notification.displayStatus = 'rejected';
        ElMessage.warning('已拒绝好友请求');
        
        // 触发拒绝事件
        emit('friend-request-rejected', notification);
      } else {
        // API返回失败：恢复原状态
        notification.displayStatus = originalStatus;
        notification.status = originalStatusBackup;
        ElMessage.error('操作失败');
      }
    } catch (error) {
      console.error('拒绝好友请求失败:', error);
      
      // 错误：恢复原状态
      notification.displayStatus = originalStatus;
      notification.status = originalStatusBackup;
      ElMessage.error(error.message || '拒绝请求时发生错误');
    }
  } else {
    // 处理群组邀请的拒绝
    try {
      // 乐观更新群组邀请状态
      const originalStatus = notification.status;
      notification.status = 'rejecting';
      
      const result = await respondToGroupInvitation(notification.id, 'reject');
      if (result) {
        notification.status = 'rejected';
        ElMessage.warning('已拒绝群组邀请');
        // 触发群组邀请拒绝事件
        emit('group-invitation-rejected', notification);
      } else {
        notification.status = originalStatus;
        ElMessage.error('拒绝群组邀请失败');
      }
    } catch (e) {
      console.error('拒绝群组邀请失败:', e);
      notification.status = originalStatus;
      ElMessage.error('拒绝群组邀请失败');
    }
  }
};

const handleCancel = async (notification) => {
  if (props.type === 'friend') {
    try {
      // 获取发送者ID和接收者ID
      const currentUserId = getCurrentUserId();
      // 确保是整数类型，使用parseInt进行转换
      const firstUserId = parseInt(currentUserId, 10);
      const secondUserId = parseInt(notification.secondUserId, 10);
      
      if (isNaN(firstUserId) || isNaN(secondUserId)) {
        ElMessage.error('无效的用户ID格式');
        return;
      }
      
      console.log('取消好友请求参数:', { firstUserId, secondUserId });
      
      // 使用API调用取消好友请求
      const result = await cancelFriendRequest(firstUserId, secondUserId);
      
      if (result) {
        ElMessage.info('已取消好友请求');
        notification.status = 'deleted';
        notification.displayStatus = 'rejected';
        // 触发取消事件
        emit('friend-request-cancelled', notification);
      } else {
        ElMessage.error('操作失败');
      }
    } catch (error) {
      console.error('取消好友请求失败:', error);
      ElMessage.error(error.message || '取消请求时发生错误');
    }
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
  margin-top: 10px;
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

:deep(.el-tag--info) {
  background-color: rgba(41, 128, 185, 0.15) !important;
  border: 1px solid var(--info-color) !important;
  color: var(--info-color) !important;
}

/* 新增：loading动画样式 */
.is-loading {
  animation: rotating 2s linear infinite;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 乐观更新状态样式 */
.el-tag.el-tag--warning.el-tag--plain {
  background-color: #fdf6ec;
  border-color: #f5dab1;
  color: #e6a23c;
}

.el-tag.el-tag--info.el-tag--plain {
  background-color: #f4f4f5;
  border-color: #d3d4d6;
  color: #909399;
}
</style> 