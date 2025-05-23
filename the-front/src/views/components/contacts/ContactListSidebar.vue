<template>
  <div class="contacts-sidebar">
    <!-- 搜索栏 -->
    <div class="search-box">
      <el-input
        v-model="searchTerm"
        placeholder="搜索"
        clearable
        >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- 通知项 -->
    <div class="notifications">
      <div
        class="notification-item"
        :class="{ active: activeNotification === 'friend' }"
        @click="showNotification('friend')"
      >
        <el-button 
          type="text" 
          :icon="MessageBox" 
          class="menu-btn"
          size="large"
        >好友通知</el-button>
        <el-badge v-if="friendNotifications.length" :value="friendNotifications.length" class="badge" type="danger" />
      </div>
      <div
        class="notification-item"
        :class="{ active: activeNotification === 'group' }"
        @click="showNotification('group')"
      >
        <el-button 
          type="text" 
          :icon="ChatRound" 
          class="menu-btn"
          size="large"
        >群通知</el-button>
        <el-badge v-if="groupNotifications.length" :value="groupNotifications.length" class="badge" type="danger" />
      </div>
    </div>

    <!-- 联系人选项卡 -->
    <div class="tabs">
      <el-button 
        @click="activeTab = 'friends'" 
        :type="activeTab === 'friends' ? 'primary' : 'default'" 
        class="tab-button"
        :icon="User"
      >好友</el-button>
      <el-button 
        @click="activeTab = 'groups'" 
        :type="activeTab === 'groups' ? 'primary' : 'default'" 
        class="tab-button"
        :icon="ChatLineRound"
      >群聊</el-button>
    </div>

    <!-- 列表区域 -->
    <div class="list-area">
      <!-- 好友列表 -->
      <div v-if="activeTab === 'friends'">
        <!-- 添加好友按钮 -->
        <div class="action-button">
          <el-button type="primary" @click="showAddFriend" icon="el-icon-plus" size="small">添加好友</el-button>
        </div>
        <div v-if="filteredFriends.length" class="contacts-list">
          <div
            v-for="friend in filteredFriends"
            :key="getFriendUserId(friend)"
            class="contact-item"
            @click="selectContact(friend, 'private')"
            :class="{ activeContact: isActive(friend, 'private') }"
          >
            <el-avatar size="large" :icon="User">{{ getFriendUsername(friend).charAt(0).toUpperCase() }}</el-avatar>
            <div class="contact-info">
              <span class="contact-name">{{ getFriendUsername(friend) }}</span>
              <span class="online-status" :class="{ 'online': friend.online }">
                {{ friend.online ? '在线' : '离线' }}
              </span>
            </div>
          </div>
        </div>
        <div v-else class="empty-tip">暂无好友</div>
      </div>

      <!-- 群列表 -->
      <div v-else-if="activeTab === 'groups'">
        <!-- 创建/加入群组按钮 -->
        <div class="action-button">
          <el-button type="primary" @click="showGroupDialog" icon="el-icon-plus" size="small">创建/加入群组</el-button>
        </div>
        <div v-if="filteredGroups.length" class="contacts-list">
          <div
            v-for="group in filteredGroups"
            :key="group.groupId"
            class="contact-item"
            @click="selectContact(group, 'group')"
            :class="{ activeContact: isActive(group, 'group') }"
          >
            <el-avatar size="large" style="background-color: #409EFF" :icon="ChatLineRound">{{ group.groupName.charAt(0).toUpperCase() }}</el-avatar>
            <span class="contact-name">{{ group.groupName }}</span>
          </div>
        </div>
        <div v-else class="empty-tip">暂无群聊</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue';
import stompClientInstance from '@/net/websocket';
import { ElMessage } from 'element-plus';
import { 
  User, 
  ChatLineRound, 
  ChatRound, 
  MessageBox, 
  Search,
  Plus,
  Bell
} from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';
import { getAuthData } from '@/utils/auth';

const emit = defineEmits(['select-contact', 'show-notifications', 'show-add-friend', 'show-group-dialog']);

// 搜索词
const searchTerm = ref('');
// 活动的通知类型（friend/group/null）
const activeNotification = ref(null);
// 活动的选项卡（friends/groups）
const activeTab = ref('friends');

// 列表数据
const friendNotifications = computed(() => stompClientInstance.friendRequests.value || []);
const groupNotifications = computed(() => stompClientInstance.groupInvitations.value || []);
const friends = computed(() => stompClientInstance.friends.value || []);
const groups = computed(() => stompClientInstance.groups.value || []);

// 获取当前用户ID
const getCurrentUserId = () => {
  // 直接从WebSocket实例获取当前用户ID
  const wsUserId = stompClientInstance.currentUserId.value;
  
  if (wsUserId) {
    return wsUserId;
  }
  
  // 作为备用方案，如果WebSocket实例还没有准备好，使用统一的认证工具
  try {
    const authData = getAuthData();
    return authData?.id || '';
  } catch (e) {
    console.error("Error parsing auth data:", e);
  }
  return '';
};

const currentUserId = computed(() => getCurrentUserId());

// 判断好友关系中当前用户是first还是second
const isCurrentUserFirst = (friend) => {
  return friend.firstUserId === currentUserId.value;
};

// 获取好友对象中对方的用户ID
const getFriendUserId = (friend) => {
  return isCurrentUserFirst(friend) ? friend.secondUserId : friend.firstUserId;
};

// 获取好友对象中对方的用户名
const getFriendUsername = (friend) => {
  return isCurrentUserFirst(friend) ? friend.secondUsername : friend.firstUsername;
};

// 过滤
const filteredFriends = computed(() => {
  if (!searchTerm.value.trim()) return friends.value;
  return friends.value.filter(f => {
    const friendUsername = getFriendUsername(f);
    return friendUsername?.toLowerCase().includes(searchTerm.value.trim().toLowerCase());
  });
});
const filteredGroups = computed(() => {
  if (!searchTerm.value.trim()) return groups.value;
  return groups.value.filter(g => g.groupName.toLowerCase().includes(searchTerm.value.trim().toLowerCase()));
});

// 显示通知（右侧区域）
const showNotification = (type) => {
  if (activeNotification.value === type) {
    // 再次点击取消选中
    activeNotification.value = null;
    emit('show-notifications', null, []);
  } else {
    activeNotification.value = type;
    if (type === 'friend') {
      emit('show-notifications', 'friend', friendNotifications.value);
    } else if (type === 'group') {
      emit('show-notifications', 'group', groupNotifications.value);
    }
  }
};

// 选中联系人/群聊
const isActive = (item, type) => {
  // 根据父组件传递的 props 判断是否选中
  return false;
};

const selectContact = (item, type) => {
  // 点击联系人时，取消通知选中状态
  activeNotification.value = null; 
  // 通知父组件关闭通知面板
  emit('show-notifications', null, []);
  
  if (type === 'private') {
    // 对于好友，创建一个简化的对象，包含对方的ID和用户名
    const friendContact = {
      userId: getFriendUserId(item),
      username: getFriendUsername(item),
      // 保留原始数据
      originalFriend: item
    };
    emit('select-contact', friendContact, type);
  } else {
    // 群组不需要特殊处理
    emit('select-contact', item, type);
  }
};

// 显示添加好友界面
const showAddFriend = () => {
  // 取消通知选中状态
  activeNotification.value = null;
  // 通知父组件显示添加好友界面
  emit('show-add-friend');
};

// 显示群组操作界面
const showGroupDialog = () => {
  // 取消通知选中状态
  activeNotification.value = null;
  // 通知父组件显示群组操作界面
  emit('show-group-dialog');
};

// 处理系统消息
const handleSystemMsg = (message) => {
  let payload;
  try {
    // 处理不同格式的消息
    payload = typeof message === 'string' ? JSON.parse(message) : message;
    if (message.body) {
      payload = JSON.parse(message.body);
    }
  } catch (e) {
    console.error('[ContactListSidebar] 无法解析系统消息:', e, message);
    return;
  }
  
  // 根据消息类型处理
  if (payload.type === 'friendRequest' || payload.type === 'friend') {
    // 已处理过的好友请求可能已存在friendsResponse字段
    if (payload.friendsResponse) {
      // 有新好友通知时，通知父组件更新
      if (activeNotification.value === 'friend') {
        emit('show-notifications', 'friend', friendNotifications.value);
      }
      ElMessage.info(`收到来自 ${payload.friendsResponse.firstUsername} 的好友请求`);
    } else {
      ElMessage.info('收到新的好友请求');
    }
  } else if (payload.type === 'group') {
    // 群组通知处理保持不变
    // 添加状态字段方便界面显示
    if (!payload.status) {
      payload.status = 'requested'; 
    }
    groupNotifications.value.push(payload);
    // 有新群组通知时，通知父组件更新
    if (activeNotification.value === 'group') {
      emit('show-notifications', 'group', groupNotifications.value);
    }
    ElMessage.info('收到新的群聊通知');
  }
};

// 监听WebSocket中群组邀请更新事件
onMounted(() => {
  console.log('[ContactListSidebar] 组件挂载，开始监听群组邀请更新');
  stompClientInstance.on('onGroupInvitationsUpdated', (invitations) => {
    console.log('[ContactListSidebar] 收到群组邀请更新事件:', invitations);
    // 如果当前正在显示群组通知，需要更新显示
    if (activeNotification.value === 'group') {
      emit('show-notifications', 'group', stompClientInstance.groupInvitations.value);
    }
    // 如果有新邀请，显示消息提示
    if (invitations && invitations.length > 0) {
      ElMessage.info(`收到${invitations.length}条群组邀请`);
    }
  });
  
  // 监听系统消息事件，处理群组邀请
  stompClientInstance.on('onSystemMessage', (message) => {
    console.log('[ContactListSidebar] 收到系统消息:', message);
    if (message.type === 'groupInvite') {
      // 收到群组邀请后立即刷新群组邀请列表
      refreshGroupInvitations();
      
      // 显示通知
      const inviterName = message.inviterName || ('用户' + message.inviterId);
      const groupName = message.groupName || ('群组' + message.groupId);
      ElMessage.info(`${inviterName} 邀请您加入群聊 ${groupName}`);
    }
  });
  
  // 首次加载时刷新群组邀请列表
  refreshGroupInvitations();
  
  // 监听好友请求更新事件
  const handleFriendRequestsUpdated = (requests) => {
    if (activeNotification.value === 'friend') {
      emit('show-notifications', 'friend', friendNotifications.value);
    }
  };
  stompClientInstance.on('friendRequestsUpdated', handleFriendRequestsUpdated);
});

onBeforeUnmount(() => {
  console.log('[ContactListSidebar] 组件卸载，移除事件监听');
  stompClientInstance.off('onGroupInvitationsUpdated');
  stompClientInstance.off('onSystemMessage');
  stompClientInstance.off('friendRequestsUpdated', handleFriendRequestsUpdated);
});

// 刷新群组邀请列表
const refreshGroupInvitations = () => {
  console.log('[ContactListSidebar] 刷新群组邀请列表');
  stompClientInstance.refreshGroupInvitations()
    .then(invitations => {
      console.log('[ContactListSidebar] 群组邀请列表已刷新:', invitations);
      // 如果当前显示的是群组通知，更新显示
      if (activeNotification.value === 'group') {
        emit('show-notifications', 'group', invitations);
      }
    })
    .catch(error => {
      console.error('[ContactListSidebar] 刷新群组邀请列表失败:', error);
    });
};
</script>

<style scoped>
.contacts-sidebar {
  width: 300px;
  background: var(--primary-color);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  height: 100%;
}
.search-box {
  padding: 8px 16px;
  margin: 0;
  border-bottom: none;
  flex-shrink: 0;
  background-color: var(--primary-color);
  display: flex;
  width: 100%;
  transition: background-color 0.2s ease;
}

/* 添加搜索框悬停效果 */
.search-box:hover {
  background-color: rgba(255, 255, 255, 0.05);
}

.search-box .el-input {
  width: 100%;
}
.search-box :deep(.el-input__wrapper) {
  background-color: rgba(0, 0, 0, 0.2) !important; /* 添加轻微黑色背景 */
  box-shadow: none !important;
  width: 100% !important;
  margin: 0 !important;
  padding: 0 !important;
  border-radius: 4px !important; /* 添加轻微圆角 */
  border: none !important;
}

/* 添加聚焦状态样式覆盖 */
.search-box :deep(.el-input__wrapper.is-focus) {
  box-shadow: none !important;
  border: none !important;
  outline: none !important;
}

/* 添加悬浮状态样式覆盖 */
.search-box :deep(.el-input__wrapper:hover) {
  box-shadow: none !important;
  border: none !important;
}

/* 修改输入框内部文字样式 */
.search-box :deep(.el-input__inner) {
  height: 32px !important;
  line-height: 32px !important;
  padding: 0 8px 0 30px !important;
  color: var(--text-secondary) !important;
  font-size: 14px !important;
  background-color: transparent !important;
}

/* 调整前缀图标样式 */
.search-box :deep(.el-input__prefix) {
  color: var(--text-secondary) !important;
  display: flex;
  align-items: center;
  padding-left: 10px !important;
  height: 100% !important;
  position: absolute !important;
  left: 0 !important;
}

/* 调整图标大小 */
.search-box :deep(.el-input__prefix .el-icon) {
  font-size: 16px !important;
}

.notifications {
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid var(--border-color);
}
.notification-item {
  padding: 5px 10px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.notification-item.active {
  background: var(--hover-color);
}
.notification-item .menu-btn {
  color: var(--text-color);
}
.tabs {
  display: flex;
  padding: 10px;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-color);
}
.tab-button {
  flex: 1;
  margin: 0 5px;
}
.list-area {
  flex: 1;
  overflow-y: auto;
  background-color: var(--primary-color);
}
.noti-list {
  padding: 10px;
}
.noti-item {
  padding: 6px 10px;
  border-bottom: 1px solid #f0f0f0;
}
.empty-tip {
  padding: 20px;
  color: var(--text-secondary);
  text-align: center;
}
.contacts-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px 10px 10px;
  background-color: var(--primary-color);
}
.contact-item {
  display: flex;
  align-items: center;
  padding: 10px;
  margin: 8px 0;
  border-radius: var(--border-radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  background-color: var(--secondary-color);
  box-shadow: var(--shadow-sm);
}
.contact-item:hover {
  background-color: var(--hover-color);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.contact-item.activeContact {
  /* background: var(--gradient-accent); */ /* 原来的明亮渐变 */
  background: linear-gradient(135deg, rgba(0, 120, 200, 0.5) 0%, rgba(0, 80, 140, 0.6) 100%); /* 更柔和的蓝色渐变 */
  color: rgba(255, 255, 255, 0.9); /* 稍微降低文本亮度 */
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 50, 100, 0.3); /* 更柔和的阴影 */
  border-left: 3px solid rgba(0, 150, 255, 0.7); /* 添加左侧边框作为轻微标识 */
}
.contact-item.activeContact .contact-name {
  color: rgba(255, 255, 255, 0.9); /* 保持与背景一致的文本透明度 */
}
.contact-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-color);
}
.contact-item.activeContact .contact-name {
  color: white;
}
.badge {
  margin-left: 4px;
}
.menu-btn {
  font-size: 14px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  color: var(--text-secondary);
}
.notification-item.active .menu-btn {
  color: var(--accent-color);
  font-weight: bold;
}
.action-button {
  padding: 10px;
  text-align: center;
  border-bottom: 1px solid var(--border-color);
  background-color: var(--secondary-color);
}
.action-button .el-button--primary {
  background-color: var(--accent-color);
  border-color: var(--accent-color);
}
.action-button .el-button--primary:hover {
  background-color: var(--accent-color-dark);
  border-color: var(--accent-color-dark);
}
.contact-info {
  display: flex;
  flex-direction: column;
  margin-left: 10px;
  flex: 1;
}

.online-status {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.online-status.online {
  color: #67c23a;
}
</style> 