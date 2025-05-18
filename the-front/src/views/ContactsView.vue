<template>
  <div class="contacts-view-container">
    <!-- Left: Contact List Sidebar -->
    <ContactListSidebar
      :friends="friends"
      :groups="groups"
      :friend-requests="friendRequests"
      :current-user-id="currentUserId"
      @select-contact="handleSelectContact"
      @show-notifications="handleShowNotifications"
      @show-add-friend="handleShowAddFriend"
      @show-group-dialog="handleShowGroupDialog"
    />
    
    <!-- Right: Detail Area -->
    <div class="detail-area">
      <!-- 好友详情 -->
      <ContactDetail
        :contact="selectedContact || null"
        :type="contactType"
        v-if="selectedContact && contactType === 'private' && !showAddFriendPanel && !showGroupPanel && !showNotifications"
      />
      
      <!-- 群组详情面板 -->
      <GroupDetailPanel
        v-if="selectedContact && contactType === 'group' && !showAddFriendPanel && !showGroupPanel && !showNotifications"
        :group-id="selectedContact.groupId"
        @invite-friend="handleInviteFriend"
        @exit-group="handleExitGroupFromDetail"
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
          @friend-request-accepted="handleFriendRequestAccepted"
          @friend-request-rejected="handleFriendRequestRejected"
          @friend-request-cancelled="handleFriendRequestCancelled"
          @group-invitation-accepted="handleGroupInvitationAccepted"
          @group-invitation-rejected="handleGroupInvitationRejected"
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

    <!-- 邀请好友加入群组对话框 -->
    <el-dialog
      v-model="inviteDialogVisible"
      title="邀请好友加入群组"
      width="500px"
    >
      <el-form :model="inviteForm" @submit.prevent="handleInviteSubmit">
        <el-alert
          title="注意: 只能邀请已是你好友的用户"
          type="warning"
          :closable="false"
          style="margin-bottom: 15px"
        />
        <el-form-item label="选择好友" prop="friendId">
          <el-select v-model="inviteForm.friendId" placeholder="选择好友" style="width: 100%">
            <el-option 
              v-for="friend in availableFriends" 
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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { Close } from '@element-plus/icons-vue';
import stompClientInstance from '@/net/websocket';
import ContactListSidebar from './components/contacts/ContactListSidebar.vue';
import ContactDetail from './components/contacts/ContactDetail.vue';
import NotificationDetail from './components/contacts/NotificationDetail.vue';
import AddFriendContent from './components/chat/AddFriendContent.vue';
import GroupContent from './components/chat/GroupContent.vue';
import GroupDetailPanel from './components/group/GroupDetailPanel.vue';
import { inviteUserToGroup } from '@/api/groupApi';
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

// 邀请好友对话框状态
const inviteDialogVisible = ref(false);
const inviteForm = ref({ friendId: null });
const inviteLoading = ref(false);

// 当前用户ID
const currentUserId = computed(() => stompClientInstance.currentUserId.value);

// 直接从 WebSocket 实例获取好友列表
const friends = computed(() => stompClientInstance.friends.value);

// 新增：直接从 WebSocket 实例获取群组列表
const groups = computed(() => stompClientInstance.groups.value);

// 从WebSocket实例获取好友请求列表
const friendRequests = computed(() => stompClientInstance.friendRequests.value);

// 可邀请好友列表（已过滤掉当前群组成员）
const availableFriends = computed(() => {
  const allFriends = friends.value || [];
  
  // 如果选中的是群组，过滤掉已经在群组中的好友
  if (selectedContact.value && contactType.value === 'group') {
    // 这里应该通过API获取当前群组成员ID列表
    // 临时使用空数组
    const groupMembers = []; 
    
    return allFriends.filter(friend => {
      // 获取好友ID
      const friendId = friend.firstUserId === currentUserId.value ? friend.secondUserId : friend.firstUserId;
      // 排除已在群组中的好友
      return !groupMembers.includes(friendId);
    }).map(friend => {
      const isFirst = friend.firstUserId === currentUserId.value;
      return {
        friendId: isFirst ? friend.secondUserId : friend.firstUserId,
        friendName: isFirst ? friend.secondUsername : friend.firstUsername
      };
    });
  }
  
  return allFriends.map(friend => {
    const isFirst = friend.firstUserId === currentUserId.value;
    return {
      friendId: isFirst ? friend.secondUserId : friend.firstUserId,
      friendName: isFirst ? friend.secondUsername : friend.firstUsername
    };
  });
});

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

// 处理好友请求接受
const handleFriendRequestAccepted = (friendRequest) => {
  // 刷新好友列表 - 使用后端API
  stompClientInstance.refreshFriends()
    .then(() => {
      console.log('好友列表刷新成功');
    })
    .catch(error => {
      console.error('刷新好友列表失败:', error);
    });
    
  // 刷新好友请求列表 - 使用后端API
  stompClientInstance.refreshFriendRequests()
    .then(requests => {
      // 更新当前显示的通知列表
      if (notificationType.value === 'friend') {
        notifications.value = requests;
      }
      
      // 如果请求列表为空，可以选择关闭通知面板
      if (requests.length === 0 && showNotifications.value) {
        setTimeout(() => {
          showNotifications.value = false;
        }, 1500);
      }
    })
    .catch(error => {
      console.error('刷新好友请求列表失败:', error);
    });
};

// 处理好友请求拒绝
const handleFriendRequestRejected = (friendRequest) => {
  // 刷新好友请求列表 - 使用后端API
  stompClientInstance.refreshFriendRequests()
    .then(requests => {
      // 更新当前显示的通知列表
      if (notificationType.value === 'friend') {
        notifications.value = requests;
      }
      
      // 如果请求列表为空，可以选择关闭通知面板
      if (requests.length === 0 && showNotifications.value) {
        setTimeout(() => {
          showNotifications.value = false;
        }, 1500);
      }
    })
    .catch(error => {
      console.error('刷新好友请求列表失败:', error);
    });
};

// 处理好友请求取消
const handleFriendRequestCancelled = (friendRequest) => {
  // 刷新好友请求列表 - 使用后端API
  stompClientInstance.refreshFriendRequests()
    .then(requests => {
      // 更新当前显示的通知列表
      if (notificationType.value === 'friend') {
        notifications.value = requests;
      }
      
      // 如果请求列表为空，可以选择关闭通知面板
      if (requests.length === 0 && showNotifications.value) {
        setTimeout(() => {
          showNotifications.value = false;
        }, 1500);
      }
    })
    .catch(error => {
      console.error('刷新好友请求列表失败:', error);
    });
};

// 处理发送好友请求
const handleFriendRequestSent = () => {
  ElMessage.success('好友请求已发送');
  
  // 可以选择关闭添加好友面板
  setTimeout(() => {
    showAddFriendPanel.value = false;
  }, 1500);
};

// 处理群组创建成功
const handleGroupCreated = (groupData) => {
  ElMessage.success(`群组"${groupData.name}"创建成功`);
  
  // 刷新群组列表
  stompClientInstance.refreshGroups()
    .then(() => {
      console.log('群组列表刷新成功');
    })
    .catch(error => {
      console.error('刷新群组列表失败:', error);
    });
    
  // 可以选择关闭群组操作面板
  setTimeout(() => {
    showGroupPanel.value = false;
  }, 1500);
};

// 处理加入群组成功
const handleGroupJoined = (groupData) => {
  ElMessage.success(`成功加入群组"${groupData.groupName}"`);
  
  // 刷新群组列表
  stompClientInstance.refreshGroups()
    .then(() => {
      console.log('群组列表刷新成功');
    })
    .catch(error => {
      console.error('刷新群组列表失败:', error);
    });
    
  // 可以选择关闭群组操作面板
  setTimeout(() => {
    showGroupPanel.value = false;
  }, 1500);
};

// 处理邀请好友加入群组
const handleInviteFriend = () => {
  if (!selectedContact.value || contactType.value !== 'group') {
    ElMessage.error('没有选中群组');
    return;
  }
  
  inviteForm.value = { friendId: null };
  inviteDialogVisible.value = true;
};

// 处理邀请提交
const handleInviteSubmit = async () => {
  if (!inviteForm.value.friendId) {
    ElMessage.warning('请选择要邀请的好友');
    return;
  }
  
  inviteLoading.value = true;
  try {
    const success = await inviteUserToGroup(selectedContact.value.groupId, inviteForm.value.friendId);
    if (success) {
      inviteDialogVisible.value = false;
      ElMessage.success('邀请已发送');
    }
  } catch (error) {
    console.error('邀请好友失败:', error);
    ElMessage.error(`邀请失败: ${error.message || '请稍后再试'}`);
  } finally {
    inviteLoading.value = false;
  }
};

// 处理群组邀请接受
const handleGroupInvitationAccepted = async (invitation) => {
  // 刷新群组列表
  try {
    await stompClientInstance.refreshGroups();
    console.log('群组列表刷新成功');
  } catch (error) {
    console.error('刷新群组列表失败:', error);
  }
  // 刷新群组邀请列表并更新展示
  try {
    const invs = await stompClientInstance.refreshGroupInvitations();
    console.log('群组邀请列表刷新成功', invs);
    if (notificationType.value === 'group') {
      notifications.value = invs;
    }
    if (!invs.length && showNotifications.value) {
      setTimeout(() => showNotifications.value = false, 1500);
    }
  } catch (error) {
    console.error('刷新群组邀请列表失败:', error);
  }
  // 关闭通知面板
  showNotifications.value = false;
};

// 处理群组邀请拒绝
const handleGroupInvitationRejected = async (invitation) => {
  // 仅刷新邀请列表并更新展示
  try {
    const invs = await stompClientInstance.refreshGroupInvitations();
    console.log('群组邀请列表刷新成功', invs);
    if (notificationType.value === 'group') {
      notifications.value = invs;
    }
    if (!invs.length && showNotifications.value) {
      setTimeout(() => showNotifications.value = false, 1500);
    }
  } catch (error) {
    console.error('刷新群组邀请列表失败:', error);
  }
};

// 处理群组退出
const handleExitGroupFromDetail = () => {
  // 退出群组后清除选中并刷新群组列表
  selectedContact.value = null;
  contactType.value = '';
  stompClientInstance.refreshGroups()
    .then(() => console.log('群组列表已刷新'))
    .catch(error => console.error('刷新群组列表失败:', error));
};

// 监听WebSocket连接状态
watch(() => stompClientInstance.isConnected.value, (connected) => {
  if (connected) {
    console.log('[ContactsView] WebSocket已连接');
  } else {
    console.log('[ContactsView] WebSocket连接断开');
  }
});

// 监听好友请求列表变化，如果正在查看好友通知，则更新通知面板内容
watch(() => stompClientInstance.friendRequests.value, (newRequests) => {
  if (showNotifications.value && notificationType.value === 'friend') {
    notifications.value = newRequests;
    console.log('好友请求列表更新，刷新通知面板:', newRequests);
  }
});

// 监听群组邀请列表变化，如果正在查看群通知，则更新通知面板内容
watch(() => stompClientInstance.groupInvitations.value, (newInvitations) => {
  if (showNotifications.value && notificationType.value === 'group') {
    notifications.value = newInvitations;
    console.log('群组邀请列表更新，刷新通知面板:', newInvitations);
  }
});

onMounted(() => {
  // 如果WebSocket已连接，立即刷新一次好友列表和好友请求
  if (stompClientInstance.isConnected.value) {
    stompClientInstance.refreshFriends()
      .catch(error => console.error('初始刷新好友列表失败:', error));
      
    stompClientInstance.refreshFriendRequests()
      .catch(error => console.error('初始刷新好友请求列表失败:', error));
      
    stompClientInstance.refreshGroups()
      .catch(error => console.error('初始刷新群组列表失败:', error));
  }
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