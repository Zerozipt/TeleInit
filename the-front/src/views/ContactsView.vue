<template>
  <div class="contacts-view-container">
    <!-- Left: Contact List Sidebar -->
    <ContactListSidebar
      @select-contact="handleSelectContact"
      @show-notifications="handleShowNotifications"
      @show-add-friend="handleShowAddFriend"
      @show-group-dialog="handleShowGroupDialog"
    />
    
    <!-- Right: Detail Area -->
    <div class="detail-area">
      <!-- 好友/群组详情 -->
      <ContactDetail
        :contact="selectedContact || null"
        :type="contactType"
        v-if="selectedContact && !showAddFriendPanel && !showGroupPanel"
      />
      
      <!-- 通知详情层 -->
      <div
        class="notification-panel"
        v-if="showNotifications && !showAddFriendPanel && !showGroupPanel"
      >
        <div class="notification-header">
          <h3>{{ notificationType === 'friend' ? '好友通知' : '群组通知' }}</h3>
          <el-button 
            type="text" 
            @click="showNotifications = false"
            :icon="Close"
            circle
          ></el-button>
        </div>
        <NotificationDetail
          :type="notificationType"
          :notifications="notifications"
        />
      </div>
      
      <!-- 添加好友面板 -->
      <div class="feature-panel" v-if="showAddFriendPanel">
        <div class="panel-header">
          <h3>添加好友</h3>
          <el-button 
            type="text" 
            @click="showAddFriendPanel = false"
            :icon="Close"
            circle
          ></el-button>
        </div>
        <div class="panel-content">
          <AddFriendContent
            :current-user-id="currentUserId"
            @friend-request-sent="handleFriendRequestSent"
          />
        </div>
      </div>
      
      <!-- 群组操作面板 -->
      <div class="feature-panel" v-if="showGroupPanel">
        <div class="panel-header">
          <h3>群组操作</h3>
          <el-button 
            type="text" 
            @click="showGroupPanel = false"
            :icon="Close"
            circle
          ></el-button>
        </div>
        <div class="panel-content">
          <GroupContent
            @group-created="handleGroupCreated"
            @group-joined="handleGroupJoined"
          />
        </div>
      </div>
      
      <!-- 空白状态 -->
      <div v-if="!selectedContact && !showNotifications && !showAddFriendPanel && !showGroupPanel" class="empty-detail">
        <el-empty description="请从联系人中选择或查看通知" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { Close } from '@element-plus/icons-vue';
import stompClientInstance from '@/net/websocket';
import ContactListSidebar from './components/contacts/ContactListSidebar.vue';
import ContactDetail from './components/contacts/ContactDetail.vue';
import NotificationDetail from './components/contacts/NotificationDetail.vue';
import AddFriendContent from './components/chat/AddFriendContent.vue';
import GroupContent from './components/chat/GroupContent.vue';
import { ElMessage, ElNotification } from 'element-plus';

// 联系人相关
const selectedContact = ref(null);
const contactType = ref('');

// 通知相关
const notificationType = ref('');
const notifications = ref([]);
const showNotifications = ref(false);

// 功能面板相关
const showAddFriendPanel = ref(false);
const showGroupPanel = ref(false);

// 当前用户ID
const currentUserId = computed(() => stompClientInstance.currentUserId.value);

// 直接从 WebSocket 实例获取好友列表
const friends = computed(() => stompClientInstance.friends.value);

// 从WebSocket实例获取好友请求列表
const friendRequests = computed(() => stompClientInstance.friendRequests.value);

// 处理选择联系人
const handleSelectContact = (contact, type) => {
  selectedContact.value = contact;
  contactType.value = type;
  // 当选择联系人时，关闭所有面板
  showNotifications.value = false;
  showAddFriendPanel.value = false;
  showGroupPanel.value = false;
};

// 处理显示通知
const handleShowNotifications = (type, items) => {
  if (!type) {
    showNotifications.value = false;
    return;
  }
  
  notificationType.value = type;
  
  // 如果是好友通知，从WebSocket实例获取最新的好友请求
  if (type === 'friend') {
    notifications.value = friendRequests.value || [];
    console.log('从WebSocket获取好友请求列表:', notifications.value);
  } else {
    notifications.value = items || [];
  }
  
  showNotifications.value = true;
  // 关闭其他面板
  showAddFriendPanel.value = false;
  showGroupPanel.value = false;
};

// 处理显示添加好友面板
const handleShowAddFriend = () => {
  showAddFriendPanel.value = true;
  // 关闭其他面板
  showNotifications.value = false;
  showGroupPanel.value = false;
  selectedContact.value = null;
};

// 处理显示群组操作面板
const handleShowGroupDialog = () => {
  showGroupPanel.value = true;
  // 关闭其他面板
  showNotifications.value = false;
  showAddFriendPanel.value = false;
  selectedContact.value = null;
};

// 处理好友请求发送成功
const handleFriendRequestSent = (userId) => {
  ElMessage.success('好友请求已发送');
  // 可以选择关闭面板
  // showAddFriendPanel.value = false;
};

// 处理群组创建成功
const handleGroupCreated = (newGroup) => {
  ElMessage.success(`群组 "${newGroup.name}" 创建成功`);
  // 刷新群组列表
  stompClientInstance.refreshGroups();
  // 可以选择关闭面板
  // showGroupPanel.value = false;
};

// 处理加入群组成功
const handleGroupJoined = (groupName) => {
  ElMessage.success(`成功加入群组 "${groupName}"`);
  // 刷新群组列表
  stompClientInstance.refreshGroups();
  // 可以选择关闭面板
  // showGroupPanel.value = false;
};

// 监听selectedContact变化，方便调试
watch(selectedContact, (newVal) => {
  console.log('selectedContact变化:', newVal);
});

// 监听WebSocket系统消息
const handleSystemMessage = (message) => {
  console.log('收到系统消息:', message);
  
  if (message.type === 'friendRequest') {
    // 显示通知
    ElNotification({
      title: '新的好友请求',
      message: `${message.senderUsername} 请求添加您为好友`,
      type: 'info',
      duration: 5000
    });
  }
};

// 组件挂载和卸载生命周期钩子
onMounted(() => {
  // 注册WebSocket系统消息监听
  stompClientInstance.on('onSystemMessage', handleSystemMessage);
});

onUnmounted(() => {
  // 移除WebSocket系统消息监听
  stompClientInstance.off('onSystemMessage', handleSystemMessage);
});
</script>

<style scoped>
.contacts-view-container {
  display: flex;
  height: 100%;
  width: 100%;
  overflow: hidden;
  background-color: var(--primary-color);
  /* box-shadow: var(--shadow-lg); */ /* 移除或调整阴影 */
  border-radius: var(--border-radius-lg); /* 保留圆角看看效果 */
  /* animation: glow 4s infinite; */ /* 移除动画 */
}

.detail-area {
  flex: 1;
  background-color: var(--secondary-color);
  display: flex;
  flex-direction: column;
  position: relative;
  transition: all var(--transition-normal);
}

.notification-panel,
.feature-panel {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--secondary-color);
  display: flex;
  flex-direction: column;
  z-index: 10;
  box-shadow: -2px 0 10px rgba(0, 0, 0, 0.5);
  border-left: 1px solid var(--border-color);
  animation: panel-slide-in 0.3s ease-out forwards;
}

@keyframes panel-slide-in {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.notification-header,
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--gradient-primary);
}

.notification-header h3,
.panel-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-color);
  background: var(--gradient-accent);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.panel-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.empty-detail {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  /* background-color: var(--primary-color); */ /* 改为 secondary 或其他更亮的颜色 */
  background-color: var(--secondary-color); /* 尝试使用 secondary color */
  height: 100%;
  /* 移除可能导致背景过暗的渐变 */
  /* background-image: 
    radial-gradient(circle at 20% 30%, rgba(0, 184, 255, 0.1) 0%, transparent 20%),
    radial-gradient(circle at 80% 70%, rgba(0, 184, 255, 0.1) 0%, transparent 20%); */
}

/* 明确设置 el-empty 的描述文字颜色 */
.empty-detail :deep(.el-empty__description) {
  color: var(--text-color); /* 确保文字可见 */
}

/* 添加霓虹灯边缘效果 */
/* @keyframes glow { */ /* 移除动画定义 */
/*   0% { */
/*     box-shadow: 0 0 5px var(--accent-color); */
/*   } */
/*   50% { */
/*     box-shadow: 0 0 15px var(--accent-color), 0 0 30px rgba(0, 184, 255, 0.3); */
/*   } */
/*   100% { */
/*     box-shadow: 0 0 5px var(--accent-color); */
/*   } */
/* } */

/* 添加悬浮效果 */
:deep(.el-empty__image) {
  animation: float 6s ease-in-out infinite;
  filter: drop-shadow(0 5px 15px rgba(0, 184, 255, 0.4));
}

@keyframes float {
  0% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-10px);
  }
  100% {
    transform: translateY(0px);
  }
}
</style> 