<template>
  <div class="chat-container">
    <!-- Sidebar -->
    <Sidebar
      :current-user="currentUser"
      :current-user-avatar="currentUserAvatar"
      :user-id="currentUserId" 
      :friends="friends"
      :groups="groups"
      :selected-contact="selectedContact"
      :chat-type="chatType"
      :unread-messages="unreadMessages"
      :latest-messages="latestMessages"
      @select-contact="handleSelectContact"
    />

    <!-- Chat Area or Welcome Screen -->
    <div class="main-chat-area">
        <ChatArea
          v-if="selectedContact"
          :contact="selectedContact"
          :type="chatType"
          :messages="currentChatMessages"
          :current-user-id="currentUserId"
          :current-user="currentUser"
          :is-loading-history="isLoadingHistory"
          :no-more-history="noMoreHistory"
          @send-message="handleSendMessage"
          @load-more-history="loadMoreHistory"
        >
          <!-- 添加群组详情按钮 -->
          <template #header-actions v-if="chatType === 'group'">
            <el-button 
              type="primary" 
              @click="showGroupDetail = !showGroupDetail" 
              class="group-info-btn"
              :icon="showGroupDetail ? ArrowRight : InfoFilled"
              text
            >
              {{ showGroupDetail ? '收起' : '群聊详情' }}
            </el-button>
          </template>
        </ChatArea>
        <WelcomeScreen v-else />
    </div>

    <!-- 群组邀请对话框 -->
    <el-dialog
      v-model="groupInvitationsVisible"
      title="群组邀请"
      width="500px"
    >
      <GroupInvitations />
    </el-dialog>

    <!-- 右侧群聊详情面板（滑动效果） -->
    <div 
      class="group-detail-sidebar" 
      :class="{ 'sidebar-open': showGroupDetail }"
      v-if="selectedContact && chatType === 'group'"
    >
      <div class="sidebar-header">
        <h3>群聊详情</h3>
        <el-button 
          type="text" 
          @click="showGroupDetail = false"
          :icon="Close"
          circle
        ></el-button>
      </div>
      <GroupDetailPanel 
        :group-id="selectedContact.groupId" 
        @invite-friend="showInviteDialog"
      />
    </div>

    <!-- 邀请好友加入群组对话框 -->
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import stompClientInstance from '@/net/websocket';
import { getPrivateChatHistory, getGroupChatHistory } from '@/api/chatApi';
import { inviteUserToGroup, getGroupDetail } from '@/api/groupApi';
import { ElMessage } from 'element-plus';
import { InfoFilled, ArrowRight, Close } from '@element-plus/icons-vue';

// Import child components
import Sidebar from './components/chat/Sidebar.vue'; 
import ChatArea from './components/chat/ChatArea.vue';
import WelcomeScreen from './components/chat/WelcomeScreen.vue';
import GroupInvitations from './components/notifications/GroupInvitations.vue';
import GroupDetailPanel from './components/group/GroupDetailPanel.vue';

// 消息提示音
const notificationSound = new Audio('/assets/notification.mp3');

// 播放提示音
const playNotificationSound = () => {
  try {
    // 检查音频文件是否加载成功
    if (notificationSound.readyState >= 2) {
      notificationSound.currentTime = 0; // 重置播放位置
      notificationSound.play()
        .catch(error => console.error('[ChatView] 播放提示音失败:', error));
    } else {
      console.warn('[ChatView] 提示音尚未加载完成');
    }
  } catch (error) {
    console.error('[ChatView] 播放提示音出错:', error);
  }
};

// 请求桌面通知权限
const requestNotificationPermission = async () => {
  try {
    if (Notification && Notification.permission !== 'granted') {
      const permission = await Notification.requestPermission();
      console.log('[ChatView] 通知权限状态:', permission);
    }
  } catch (error) {
    console.error('[ChatView] 请求通知权限失败:', error);
  }
};

// 发送桌面通知
const sendNotification = (title, body) => {
  try {
    if (Notification && Notification.permission === 'granted' && document.visibilityState !== 'visible') {
      const notification = new Notification(title, {
        body: body,
        icon: '/favicon.ico', // 可以替换为您自己的图标
      });
      
      // 点击通知时聚焦窗口
      notification.onclick = function() {
        window.focus();
        this.close();
      };
      
      // 5秒后自动关闭通知
      setTimeout(() => notification.close(), 5000);
    }
  } catch (error) {
    console.error('[ChatView] 发送通知失败:', error);
  }
};

// 页面标题闪烁提示
const originalTitle = document.title;
let titleInterval = null;

// 开始标题闪烁
const startTitleFlashing = (message) => {
  // 如果已经有一个闪烁间隔，则不需要再次启动
  if (titleInterval) return;
  
  const newTitle = message || '新消息';
  let isOriginal = false;
  
  titleInterval = setInterval(() => {
    document.title = isOriginal ? originalTitle : `(${newTitle}) ${originalTitle}`;
    isOriginal = !isOriginal;
  }, 1000);
  
  // 用户切换回页面时停止闪烁
  window.addEventListener('focus', stopTitleFlashing);
};

// 停止标题闪烁
const stopTitleFlashing = () => {
  if (titleInterval) {
    clearInterval(titleInterval);
    titleInterval = null;
    document.title = originalTitle;
  }
  
  // 移除事件监听器以避免重复添加
  window.removeEventListener('focus', stopTitleFlashing);
};

// --- Core State ---
const currentUser = ref('');
const currentUserId = ref('');
const currentUserAvatar = ref(''); 
const friends = ref([]);
const groups = ref([]);
const selectedContact = ref(null); 
const chatType = ref(''); // 'private' or 'group'
const chatHistories = ref(new Map());
const isLoadingHistory = ref(false);
const noMoreHistory = ref(false);

// 添加消息状态管理
const MESSAGE_STATUS = {
  SENDING: 'sending',     // 发送中
  SENT: 'sent',          // 已发送到服务器
  DELIVERED: 'delivered', // 已送达对方
  FAILED: 'failed',      // 发送失败
  READ: 'read'           // 已读
};

// 消息状态映射 - 存储临时消息ID到真实消息ID的映射
const messageStatusMap = ref(new Map());
// 待确认的消息队列
const pendingMessages = ref(new Map());

// 添加未读消息管理
const unreadMessages = ref(new Map()); // 存储每个联系人/群组的未读消息数量
const latestMessages = ref(new Map()); // 存储每个联系人/群组的最新消息内容

// 对话框状态
const groupInvitationsVisible = ref(false);
const showGroupDetail = ref(false);
const inviteDialogVisible = ref(false);
const inviteForm = ref({ friendId: null });
const inviteLoading = ref(false);

// 计算当前聊天消息
const currentChatMessages = computed(() => {
  if (!selectedContact.value) {
    return [];
  }
  const key = chatType.value === 'private' ? selectedContact.value.userId : selectedContact.value.groupId;
  const msgs = chatHistories.value.get(key) || [];
  return [...msgs].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
});

// 可邀请好友列表（已过滤掉当前群组成员）
const availableFriends = computed(() => {
  const allFriends = stompClientInstance.friends.value || [];
  
  // 如果选中的是群组，过滤掉已经在群组中的好友
  if (selectedContact.value && chatType.value === 'group') {
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

// --- 获取JWT令牌 ---
const getJwtToken = () => {
  try {
    const authData = localStorage.getItem('authorize');
    if (authData) {
      const parsedAuth = JSON.parse(authData);
      return parsedAuth?.token || null;
    }
  } catch (e) {
    console.error("Error reading auth token from localStorage:", e);
  }
  return null;
};

// --- WebSocket同步和初始化 ---

// 从WebSocket实例同步数据到组件的状态
const syncDataFromWebSocket = () => {
  console.log('[ChatView] 从WebSocket实例同步数据...');
  currentUser.value = stompClientInstance.currentUser.value;
  currentUserId.value = stompClientInstance.currentUserId.value;
  friends.value = stompClientInstance.friends.value;
  groups.value = stompClientInstance.groups.value;
  currentUserAvatar.value = currentUser.value ? currentUser.value.substring(0, 1).toUpperCase() : 'U';

  console.log('[ChatView] 数据同步:', { currentUser: currentUser.value, friends: friends.value.length, groups: groups.value.length });

  // 每次同步时初始化本地历史（从全局缓存读取）
  initializeChatHistories();
};

// 添加对好友列表和群组列表变化的监听
watch(() => stompClientInstance.friends.value, (newFriends) => {
  console.log('[ChatView] 检测到好友列表变化');
  friends.value = newFriends;
}, { deep: true });

watch(() => stompClientInstance.groups.value, (newGroups) => {
  console.log('[ChatView] 检测到群组列表变化');
  groups.value = newGroups;
}, { deep: true });

// 初始化本地chatHistories Map
const initializeChatHistories = () => {
  console.log('[ChatView] 从WebSocket实例初始化聊天历史...');
  const initialHistories = new Map();
  // 私聊历史
  stompClientInstance.privateMessages.value.forEach(raw => {
    const msg = normalizeMessage(raw, 'private');
    // 若是自己发送的消息，则使用 receiverId，否则使用 senderId
    const cid = msg.senderId === Number(currentUserId.value) ? msg.receiverId : msg.senderId;
    const idKey = String(cid);
    if (!initialHistories.has(idKey)) {
      initialHistories.set(idKey, []);
    }
    initialHistories.get(idKey).push(msg);
  });
  // 群聊历史
  stompClientInstance.groupMessages.value.forEach((list, groupId) => {
    list.forEach(raw => {
      const msg = normalizeMessage(raw, 'group');
      const idKey = String(groupId);
      if (!initialHistories.has(idKey)) {
        initialHistories.set(idKey, []);
      }
      initialHistories.get(idKey).push(msg);
    });
  });
  // 排序
  initialHistories.forEach(arr => {
    arr.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
  });
  chatHistories.value = initialHistories;
  console.log('[ChatView] 聊天历史初始化并排序。', chatHistories.value.size);
};

// 注册WebSocket事件处理程序
const registerWebSocketHandlers = () => {
    console.log('[ChatView] 注册WebSocket处理程序...');
    // 确保在添加新处理程序之前删除以前的处理程序
    stompClientInstance.off('onPrivateMessage', handlePrivateMessage);
    stompClientInstance.off('onPublicMessage', handlePublicMessage);
    stompClientInstance.off('onError', handleError);
    stompClientInstance.off('onMessageAck', handleMessageAck);

    stompClientInstance.on('onPrivateMessage', handlePrivateMessage);
    stompClientInstance.on('onPublicMessage', handlePublicMessage);
    stompClientInstance.on('onError', handleError);
    stompClientInstance.on('onMessageAck', handleMessageAck);
    console.log('[ChatView] WebSocket处理程序已注册。');
};

// --- 生命周期钩子 ---
onMounted(async () => {
  console.log('[ChatView] 组件挂载。');
  const jwt = getJwtToken();
  if (!jwt) {
    ElMessage.error('未登录，请先登录');
    // 考虑重定向到登录
    return;
  }

  // 请求通知权限
  await requestNotificationPermission();

  if (!stompClientInstance.isConnected.value) {
      console.log('[ChatView] WebSocket未连接，尝试连接...');
      try {
          // websocket.js中的connect方法现在处理getUserInfo和初始订阅
          await stompClientInstance.connect(jwt);
          // 初次连接成功，立即同步数据并注册处理程序，确保渲染初始聊天历史
          console.log('[ChatView] 初次连接成功，立即同步历史记录并注册处理程序');
          syncDataFromWebSocket();
          registerWebSocketHandlers();
      } catch (error) {
          console.error('[ChatView] 连接WebSocket失败:', error);
          ElMessage.error(`WebSocket 连接失败: ${error?.message || '请检查网络或服务'}`);
      }
  } else {
      console.log('[ChatView] WebSocket已连接。同步数据并注册处理程序。');
      // 如果已经连接，立即同步数据并注册处理程序
      syncDataFromWebSocket();
      registerWebSocketHandlers();
  }

  // 当 WebSocket 连接成功时, 在 syncDataFromWebSocket 里已调用 initializeChatHistories
  // 但如果 groupMessages/privateMessages 在 connect 之后才赋值，则需要在 onConnected 回调后也初始化
  stompClientInstance.on('onConnected', () => {
    // 连接获取到所有数据后，确保初始化历史
    console.log('[ChatView] onConnected 事件，初始化聊天历史');
    initializeChatHistories();
    // 注册处理程序
    registerWebSocketHandlers();
  });
});

// 监听连接状态变化
watch(stompClientInstance.isConnected, (newValue, oldValue) => {
    console.log(`[ChatView] WebSocket连接状态变化: ${oldValue} -> ${newValue}`);
    if (newValue === true) {
        // 连接建立
        console.log('[ChatView] 连接建立。同步数据并注册处理程序。');
        syncDataFromWebSocket();
        registerWebSocketHandlers();
    } else {
        // 连接丢失
        console.log('[ChatView] WebSocket连接丢失。');
        // 可选地清除一些状态或显示断开连接的消息
        // selectedContact.value = null;
        // chatType.value = '';
    }
});

onUnmounted(() => {
    console.log('[ChatView] 组件卸载。移除WebSocket处理程序。');
    // 组件销毁时清理监听器
    stompClientInstance.off('onPrivateMessage', handlePrivateMessage);
    stompClientInstance.off('onPublicMessage', handlePublicMessage);
    stompClientInstance.off('onError', handleError);
    stompClientInstance.off('onMessageAck', handleMessageAck);
    
    // 清理全局函数
    if (window.retryMessage) delete window.retryMessage;
    if (window.cancelMessage) delete window.cancelMessage;
    
    // 清理定时器和状态
    stopTitleFlashing();
    pendingMessages.value.clear();
    messageStatusMap.value.clear();
});

// --- WebSocket事件处理程序 ---
const handleConnected = (username) => {
  // 这可能是不必要的，如果使用watch(isConnected)
  console.log('[ChatView] WebSocket onConnected事件收到。用户名:', username);
  // syncDataFromWebSocket(); 
  // registerWebSocketHandlers();
};

const handlePrivateMessage = (message) => {
  console.log('[ChatView] 收到私聊消息:', message);
  
  const msg = normalizeMessage(message, 'private');
  
  // 检查是否是自己发送的消息的确认
  if (Number(msg.senderId) === Number(currentUserId.value)) {
    console.log('[ChatView] 这是自己发送的消息确认');
    // 这是自己发送的消息确认，检查是否已有乐观更新的消息
    const cid = msg.receiverId;
    const idKey = String(cid);
    const existing = chatHistories.value.get(idKey) || [];
    
    // 查找对应的乐观更新消息
    const optimisticMsgIndex = existing.findIndex(existingMsg => 
      existingMsg.isOptimistic && 
      existingMsg.senderId === msg.senderId &&
      existingMsg.content === msg.content &&
      Math.abs(new Date(existingMsg.timestamp) - new Date(msg.timestamp)) < 30000 // 30秒内
    );
    
    if (optimisticMsgIndex !== -1) {
      // 找到对应的乐观更新消息，更新其状态和ID
      const optimisticMsg = existing[optimisticMsgIndex];
      optimisticMsg.id = msg.id;
      optimisticMsg.status = MESSAGE_STATUS.SENT;
      optimisticMsg.isOptimistic = false;
      optimisticMsg.timestamp = msg.timestamp; // 使用服务器时间
      
      // 从待确认队列中移除
      pendingMessages.value.forEach((pendingMsg, tempId) => {
        if (pendingMsg.message === optimisticMsg) {
          pendingMessages.value.delete(tempId);
        }
      });
      
      console.log('[ChatView] 乐观更新消息已确认:', optimisticMsg);
      chatHistories.value = new Map(chatHistories.value);
      return; // 不需要添加新消息
    }
  } else {
    console.log('[ChatView] 这是收到的他人消息');
  }
  
  // 检查是否是重复消息（针对接收到的消息）
  const cid = msg.senderId === Number(currentUserId.value) ? msg.receiverId : msg.senderId;
  const idKey = String(cid);
  const existing = chatHistories.value.get(idKey) || [];
  
  console.log(`[ChatView] 检查重复消息 - 聊天ID: ${idKey}, 现有消息数量: ${existing.length}`);
  console.log(`[ChatView] 消息详情 - ID: ${msg.id}, 发送者: ${msg.senderId}, 内容: ${msg.content}, tempId: ${msg.tempId}`);
  
  // 检查是否已存在相同消息（通过ID或内容+时间戳）
  // 注意：不要使用tempId来判断重复，因为接收者不会有对应的tempId消息
  const isDuplicate = existing.some(existingMsg => {
    // 如果有真实的消息ID，优先使用ID比较
    if (msg.id && existingMsg.id && msg.id === existingMsg.id) {
      console.log(`[ChatView] 发现重复消息（ID匹配）: ${msg.id}`);
      return true;
    }
    
    // 否则使用发送者+内容+时间戳比较，但排除乐观更新的消息
    if (!existingMsg.isOptimistic && 
        existingMsg.senderId === msg.senderId && 
        existingMsg.content === msg.content && 
        Math.abs(new Date(existingMsg.timestamp) - new Date(msg.timestamp)) < 1000) {
      console.log(`[ChatView] 发现重复消息（内容+时间戳匹配）`);
      return true;
    }
    
    return false;
  });
  
  if (isDuplicate) {
    console.log('[ChatView] 忽略重复的私聊消息:', msg);
    return;
  }
  
  console.log('[ChatView] 添加新的私聊消息');
  // 添加新消息
  if (!chatHistories.value.has(idKey)) {
    chatHistories.value.set(idKey, []);
  }
  chatHistories.value.get(idKey).push(msg);
  chatHistories.value = new Map(chatHistories.value);
  
  // 更新最新消息内容
  if (msg.fileUrl) {
    const fileType = msg.messageType || '文件';
    latestMessages.value.set(idKey, `[${fileType}] ${msg.fileName || '文件'}`);
  } else {
    latestMessages.value.set(idKey, msg.content);
  }
  
  // 如果不是当前选中的联系人，增加未读消息计数
  if (!selectedContact.value || 
      chatType.value !== 'private' || 
      String(selectedContact.value.userId) !== idKey) {
    const currentCount = unreadMessages.value.get(idKey) || 0;
    unreadMessages.value.set(idKey, currentCount + 1);
    
    // 播放提示音和发送通知（仅针对他人发送的消息）
    if (Number(msg.senderId) !== Number(currentUserId.value)) {
      playNotificationSound();
      const senderName = msg.sender || '好友';
      sendNotification(`来自 ${senderName} 的新消息`, msg.content);
      
      if (document.visibilityState !== 'visible') {
        startTitleFlashing('新消息');
      }
    }
  }
};

const handlePublicMessage = (message) => {
  console.log('[ChatView] 收到群聊消息:', message);
  
  const msg = normalizeMessage(message, 'group');
  
  // 检查是否是自己发送的消息的确认
  if (Number(msg.senderId) === Number(currentUserId.value)) {
    console.log('[ChatView] 这是自己发送的群聊消息确认');
    // 这是自己发送的消息确认，检查是否已有乐观更新的消息
    const idKey = String(msg.groupId);
    const existing = chatHistories.value.get(idKey) || [];
    
    // 查找对应的乐观更新消息
    const optimisticMsgIndex = existing.findIndex(existingMsg => 
      existingMsg.isOptimistic && 
      existingMsg.senderId === msg.senderId &&
      existingMsg.content === msg.content &&
      Math.abs(new Date(existingMsg.timestamp) - new Date(msg.timestamp)) < 30000 // 30秒内
    );
    
    if (optimisticMsgIndex !== -1) {
      // 找到对应的乐观更新消息，更新其状态和ID
      const optimisticMsg = existing[optimisticMsgIndex];
      optimisticMsg.id = msg.id;
      optimisticMsg.status = MESSAGE_STATUS.SENT;
      optimisticMsg.isOptimistic = false;
      optimisticMsg.timestamp = msg.timestamp; // 使用服务器时间
      
      // 从待确认队列中移除
      pendingMessages.value.forEach((pendingMsg, tempId) => {
        if (pendingMsg.message === optimisticMsg) {
          pendingMessages.value.delete(tempId);
        }
      });
      
      console.log('[ChatView] 群聊乐观更新消息已确认:', optimisticMsg);
      chatHistories.value = new Map(chatHistories.value);
      return; // 不需要添加新消息
    }
  } else {
    console.log('[ChatView] 这是收到的他人群聊消息');
  }
  
  // 检查是否是重复消息
  const idKey = String(msg.groupId);
  const existing = chatHistories.value.get(idKey) || [];
  
  console.log(`[ChatView] 检查重复群聊消息 - 群组ID: ${idKey}, 现有消息数量: ${existing.length}`);
  console.log(`[ChatView] 群聊消息详情 - ID: ${msg.id}, 发送者: ${msg.senderId}, 内容: ${msg.content}, tempId: ${msg.tempId}`);
  
  // 检查是否已存在相同消息（通过ID或内容+时间戳）
  // 注意：不要使用tempId来判断重复，因为接收者不会有对应的tempId消息
  const isDuplicate = existing.some(existingMsg => {
    // 如果有真实的消息ID，优先使用ID比较
    if (msg.id && existingMsg.id && msg.id === existingMsg.id) {
      console.log(`[ChatView] 发现重复群聊消息（ID匹配）: ${msg.id}`);
      return true;
    }
    
    // 否则使用发送者+内容+时间戳比较，但排除乐观更新的消息
    if (!existingMsg.isOptimistic && 
        existingMsg.senderId === msg.senderId && 
        existingMsg.content === msg.content && 
        Math.abs(new Date(existingMsg.timestamp) - new Date(msg.timestamp)) < 1000) {
      console.log(`[ChatView] 发现重复群聊消息（内容+时间戳匹配）`);
      return true;
    }
    
    return false;
  });
  
  if (isDuplicate) {
    console.log('[ChatView] 忽略重复的群聊消息:', msg);
    return;
  }
  
  console.log('[ChatView] 添加新的群聊消息');
  // 添加新消息
  if (!chatHistories.value.has(idKey)) {
    chatHistories.value.set(idKey, []);
  }
  chatHistories.value.get(idKey).push(msg);
  chatHistories.value = new Map(chatHistories.value);
  
  // 更新最新消息内容
  if (msg.fileUrl) {
    const fileType = msg.messageType || '文件';
    latestMessages.value.set(idKey, `${msg.sender || '有人'}: [${fileType}] ${msg.fileName || '文件'}`);
  } else {
    latestMessages.value.set(idKey, `${msg.sender || '有人'}: ${msg.content}`);
  }
  
  // 如果不是当前选中的群组，增加未读消息计数
  if (!selectedContact.value || 
      chatType.value !== 'group' || 
      String(selectedContact.value.groupId) !== idKey) {
    const currentCount = unreadMessages.value.get(idKey) || 0;
    unreadMessages.value.set(idKey, currentCount + 1);
    
    // 播放提示音和发送通知（仅针对他人发送的消息）
    if (Number(msg.senderId) !== Number(currentUserId.value)) {
      playNotificationSound();
      const groupName = findGroupName(idKey) || '群聊';
      const senderName = msg.sender || '有人';
      sendNotification(`${groupName} 的新消息`, `${senderName}: ${msg.content}`);
      
      if (document.visibilityState !== 'visible') {
        startTitleFlashing('新消息');
      }
    }
  }
};

const handleError = (error) => {
  console.error('[ChatView] WebSocket错误收到:', error);
  ElMessage.error(`WebSocket 错误: ${error}`);
};

// --- Event Handlers from Child Components ---
const handleSelectContact = async (contact, type) => {
  console.log('[ChatView] handleSelectContact被调用:', type, contact);
  if (!contact) {
    console.error('[ChatView] 选中的联系人无效');
    selectedContact.value = null;
    chatType.value = '';
    return;
  }
  // 重置历史加载状态
  isLoadingHistory.value = false;
  noMoreHistory.value = false;
  
  // 如果是群组类型，先获取群组详情
  if (type === 'group') {
    try {
      // 获取群组详情，包括成员数量
      const groupDetail = await getGroupDetail(contact.groupId);
      if (groupDetail) {
        // 更新联系人对象，添加成员数量
        contact.memberCount = groupDetail.members?.length || 0;
      }
    } catch (error) {
      console.error('[ChatView] 获取群组详情失败:', error);
      // 失败时不阻止继续，只是没有成员数量信息
    }
  } else if (type === 'private') {
    // 如果是私聊类型，从friends列表中查找并设置online状态
    try {
      const friend = friends.value.find(f => {
        // 需要进行字符串比较，因为ID可能是字符串也可能是数字
        const friendId = f.firstUserId === currentUserId.value.toString() ? f.secondUserId : f.firstUserId;
        return friendId === contact.userId.toString();
      });
      
      if (friend) {
        console.log('[ChatView] 找到好友，设置在线状态:', friend);
        // 明确设置在线状态，确保不是undefined
        contact.online = friend.online === true;
      } else {
        console.warn('[ChatView] 未找到对应好友，无法设置在线状态:', contact);
        // 默认设置为离线
        contact.online = false;
      }
    } catch (error) {
      console.error('[ChatView] 设置好友在线状态时出错:', error);
      contact.online = false;
    }
  }
  
  // 设置当前选择
  selectedContact.value = contact;
  chatType.value = type;
  // 计算 Map 的 key 并强制为字符串
  const key = type === 'private' ? String(contact.userId) : String(contact.groupId);
  // 确保历史条目存在
  if (!chatHistories.value.has(key)) {
    console.log(`[ChatView] 没有初始历史，为${key}创建空条目。`);
    chatHistories.value.set(key, []);
  } else {
    console.log(`[ChatView] 显示现有的历史，${key}`);
  }
  
  // 添加: 选中联系人后，清除对应的未读消息计数
  if (unreadMessages.value.has(key)) {
    unreadMessages.value.delete(key);
  }
  
  // 切换联系人时自动关闭群聊详情
  showGroupDetail.value = false;
};

const handleSendMessage = (message) => {
  // 检查是否是文件消息 - 判断是否有fileUrl字段
  const isFileMessage = message && message.fileUrl;
  console.log(`[ChatView] 处理${isFileMessage ? '文件' : '文本'}消息发送:`, message);

  if (!selectedContact.value) {
    ElMessage.warning('请先选择聊天对象');
    return;
  }

  // 生成临时消息ID
  const tempId = 'temp_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
  const currentTime = new Date().toISOString();
  
  // 构建基本消息内容
  const content = isFileMessage ? message.content : (typeof message === 'string' ? message.trim() : message.content);
  
  if (!content && !isFileMessage) {
    ElMessage.warning('消息内容不能为空');
    return;
  }

  // 创建乐观更新消息对象
  const optimisticMessage = {
    id: tempId,
    type: 'CHAT',
    content: content,
    timestamp: currentTime,
    senderId: Number(currentUserId.value),
    sender: currentUser.value,
    status: MESSAGE_STATUS.SENDING, // 标记为发送中
    isOptimistic: true, // 标记为乐观更新消息
    // 文件相关字段
    fileUrl: isFileMessage ? message.fileUrl : null,
    fileName: isFileMessage ? message.fileName : null,
    fileType: isFileMessage ? message.fileType : null,
    fileSize: isFileMessage ? message.fileSize : null,
    messageType: isFileMessage ? (message.messageType || 'FILE') : null
  };

  // 处理群聊消息
  if (chatType.value === 'group') {
    const groupId = selectedContact.value.groupId;
    if (!groupId) {
      ElMessage.error('未选择有效的群聊');
      return;
    }
    
    optimisticMessage.groupId = groupId;
    optimisticMessage.receiverId = null;
    
    // 立即添加到聊天历史（乐观更新）
    const key = String(groupId);
    if (!chatHistories.value.has(key)) {
      chatHistories.value.set(key, []);
    }
    chatHistories.value.get(key).push(optimisticMessage);
    
    // 存储待确认消息
    pendingMessages.value.set(tempId, {
      message: optimisticMessage,
      type: 'group',
      targetId: groupId,
      retryCount: 0
    });
    
    // 发送到后端
    try {
      if (isFileMessage) {
        stompClientInstance.sendPublicMessage(content, groupId, message);
      } else {
        stompClientInstance.sendPublicMessage(content, groupId);
      }
      
      // 设置超时处理
      setTimeout(() => {
        if (pendingMessages.value.has(tempId)) {
          updateMessageStatus(tempId, MESSAGE_STATUS.FAILED);
          ElMessage.error('消息发送超时，请检查网络连接');
        }
      }, 10000); // 10秒超时
      
    } catch (error) {
      console.error('[ChatView] 发送群聊消息失败:', error);
      updateMessageStatus(tempId, MESSAGE_STATUS.FAILED);
      ElMessage.error('发送失败: ' + error.message);
    }
  }
  // 处理私聊消息
  else if (chatType.value === 'private') {
    const receiverId = selectedContact.value.userId;
    const receiverName = selectedContact.value.username;
    
    if (!receiverId) {
      ElMessage.error('未选择有效的私聊对象');
      return;
    }
    
    optimisticMessage.receiverId = Number(receiverId);
    optimisticMessage.groupId = null;
    
    // 立即添加到聊天历史（乐观更新）
    const key = String(receiverId);
    if (!chatHistories.value.has(key)) {
      chatHistories.value.set(key, []);
    }
    chatHistories.value.get(key).push(optimisticMessage);
    
    // 存储待确认消息
    pendingMessages.value.set(tempId, {
      message: optimisticMessage,
      type: 'private',
      targetId: receiverId,
      receiverName: receiverName,
      retryCount: 0
    });
    
    // 发送到后端
    try {
      if (isFileMessage) {
        stompClientInstance.sendPrivateMessage(receiverId, content, receiverName, message);
      } else {
        stompClientInstance.sendPrivateMessage(receiverId, content, receiverName);
      }
      
      // 设置超时处理
      setTimeout(() => {
        if (pendingMessages.value.has(tempId)) {
          updateMessageStatus(tempId, MESSAGE_STATUS.FAILED);
          ElMessage.error('消息发送超时，请检查网络连接');
        }
      }, 10000); // 10秒超时
      
    } catch (error) {
      console.error('[ChatView] 发送私聊消息失败:', error);
      updateMessageStatus(tempId, MESSAGE_STATUS.FAILED);
      ElMessage.error('发送失败: ' + error.message);
    }
  }

  // 触发界面更新并滚动到底部
  chatHistories.value = new Map(chatHistories.value);
  nextTick(() => {
    // 强制滚动到底部显示新消息
    const chatArea = document.querySelector('.messages-container');
    if (chatArea) {
      chatArea.scrollTop = chatArea.scrollHeight;
    }
  });
  
  console.log('[ChatView] 乐观更新消息已添加:', optimisticMessage);
};

// 新函数手动加载更早的历史
const loadMoreHistory = async () => {
    if (!selectedContact.value || isLoadingHistory.value || noMoreHistory.value) {
        console.log('[ChatView] 无法加载更多历史:', { isLoading: isLoadingHistory.value, noMore: noMoreHistory.value });
        return;
    }

    const contact = selectedContact.value;
    const type = chatType.value;
    const key = type === 'private' ? contact.userId : contact.groupId;
    const currentHistory = chatHistories.value.get(key) || [];
    const oldestMessageId = currentHistory.length > 0 ? currentHistory[0].id : null; 
    console.log(`[ChatView] 尝试加载更早的历史, 在消息ID: ${oldestMessageId}之前`);

    isLoadingHistory.value = true;  
    try {
        let olderHistory = [];
        const fetchLimit = 50;
        
        if (type === 'private') {
            olderHistory = await getPrivateChatHistory(
                currentUserId.value,
                contact.userId, 
                fetchLimit,
                oldestMessageId
            );
        } else if (type === 'group') {
            olderHistory = await getGroupChatHistory(contact.groupId, fetchLimit, oldestMessageId);
        }

        // 检查结果是否是数组
        if (!Array.isArray(olderHistory)) {
            console.log('[ChatView] 返回的历史记录不是数组');
            olderHistory = [];
        }

        console.log(`[ChatView] 获取更早的历史: ${key}:`, olderHistory.length);

        if (olderHistory.length > 0) {
            // 过滤掉任何已经存在的消息
            const existingIds = new Set(currentHistory.map(m => m.id));
            const newMessages = olderHistory.filter(m => m && m.id && !existingIds.has(m.id));

            if (newMessages.length > 0) {
                const normalized = newMessages.map(raw => normalizeMessage(raw, chatType.value));
                chatHistories.value.set(key, [...normalized, ...currentHistory]);
            } else {
                console.log(`[ChatView] 没有找到新的更早的消息: ${key}.`);
                noMoreHistory.value = true;
                ElMessage.info('没有更早的消息了');
            }
            
            if (olderHistory.length < fetchLimit) {
                console.log(`[ChatView] 获取的更早消息小于限制(${fetchLimit}), 没有更多历史了`);
                noMoreHistory.value = true;
                ElMessage.info('没有更早的消息了');
            }
        } else {
            console.log(`[ChatView] 没有返回更早的历史: ${key}.`);
            noMoreHistory.value = true;
            ElMessage.info('没有更早的消息了');
        }
    } catch (error) {
        console.error('[ChatView] 通过API加载更早的聊天记录失败:', error);
        ElMessage.error(`加载失败: ${error.message || '请稍后重试'}`);
        // 即使失败也标记为没有更多历史，防止用户一直点击
        noMoreHistory.value = true;
    } finally {
        isLoadingHistory.value = false;
    }
};

// 添加统一消息格式转换函数
const normalizeMessage = (rawMsg, type) => {
  // 若消息有 sender 字段，视为已是 ChatMessage 格式，直接返回
  if (rawMsg.sender) {
    // 确保 senderId 和 receiverId 为数字类型
    const cleanMsg = { ...rawMsg };
    if (cleanMsg.senderId) cleanMsg.senderId = Number(cleanMsg.senderId);
    if (cleanMsg.receiverId) cleanMsg.receiverId = Number(cleanMsg.receiverId);
    return cleanMsg;
  }
  
  // 否则将后端 DTO 转为 ChatMessage 格式
  const chatMsg = {
    type: 'CHAT',
    content: rawMsg.content,
    timestamp: rawMsg.timestamp || rawMsg.createdAt || rawMsg.createAt || rawMsg.CreateAt,
    senderId: Number(rawMsg.senderId || rawMsg.SenderId),
    sender: '', // sender 初始化为空字符串
    roomId: null,
    groupId: type === 'group' ? rawMsg.groupId : null,
    receiverId: type === 'private' ? Number(rawMsg.receiverId) : null,
    // 直接从原始消息中获取senderName，如果存在的话
    // 因为后端处理历史消息时会填充senderName
    senderName: rawMsg.senderName || null,
    // 保留文件相关字段
    fileUrl: rawMsg.fileUrl || rawMsg.file_url || null,
    fileName: rawMsg.fileName || null,
    fileType: rawMsg.fileType || null,
    fileSize: rawMsg.fileSize || null,
    messageType: rawMsg.messageType || (rawMsg.fileUrl || rawMsg.file_url ? 'FILE' : null)
  };
  
  // 补齐 sender 用户名
  if (chatMsg.senderId === Number(currentUserId.value)) {
    chatMsg.sender = currentUser.value;
  } else {
    if (type === 'private') {
      // 私聊逻辑：尝试从好友列表匹配接收者/发送者以获得正确的用户名
      const friend = friends.value.find(f => {
        const friendId = String(f.firstUserId) === String(chatMsg.senderId) ? String(f.secondUserId) : String(f.firstUserId);
        const otherPartyIdInMsg = String(f.firstUserId) === String(chatMsg.senderId) ? String(chatMsg.receiverId) : String(chatMsg.senderId);
        // 确保好友关系中的另一方是消息的接收者或发送者
        // 且消息的发送者是我们正在查找的对方
        return (String(f.firstUserId) === String(chatMsg.senderId) && String(f.secondUserId) === String(chatMsg.receiverId)) ||
               (String(f.secondUserId) === String(chatMsg.senderId) && String(f.firstUserId) === String(chatMsg.receiverId));
      });

      if (friend) {
         // 如果当前登录用户是好友关系中的 a，那么消息发送者 b 的用户名是 friend.secondUsername
         // 如果当前登录用户是好友关系中的 b，那么消息发送者 a 的用户名是 friend.firstUsername
         // 但我们是为非自己的消息填充 sender，所以直接用 rawMsg.senderName (如果后端有) 或通过 senderId 查
         // 对于 normalizeMessage 来说，它应该从 rawMsg 中获取信息
         chatMsg.sender = rawMsg.senderName || '未知用户'; // 优先用后端提供的 senderName
      } else {
         chatMsg.sender = rawMsg.senderName || '未知用户'; // 保底
      }

    } else if (type === 'group') {
      // 群聊消息：优先使用后端直接提供的 senderName
      if (rawMsg.senderName) {
        chatMsg.sender = rawMsg.senderName;
      } else if (rawMsg.sender) { // 兼容可能仍然使用 sender 字段的情况
        chatMsg.sender = rawMsg.sender;
      } else {
        // 如果后端既没有 senderName 也没有 sender，作为最后手段
        // 但理论上后端现在总会有 senderName
        // logger.warn(`[ChatView] Group message from senderId ${chatMsg.senderId} missing senderName and sender.`);
        chatMsg.sender = '未知用户'; 
      }
    }
  }
  // 如果经过上述逻辑后 chatMsg.sender 仍为空，但 chatMsg.senderName 有值，则使用它
  // 这主要针对非自己发送的消息，因为自己的消息 sender 会被 currentUser.value 覆盖
  if (!chatMsg.sender && chatMsg.senderName) {
    chatMsg.sender = chatMsg.senderName;
  }

  return chatMsg;
};

// 辅助函数: 通过 groupId 查找群组名
const findGroupName = (groupId) => {
  const group = groups.value.find(g => g.groupId === groupId);
  return group ? (group.groupName || group.name) : null;
};

// 群组邀请相关
const showGroupInvitations = () => {
  groupInvitationsVisible.value = true;
};

// 显示邀请好友对话框
const showInviteDialog = () => {
  inviteForm.value = { friendId: null };
  inviteDialogVisible.value = true;
};

// 处理邀请好友加入群组
const handleInvite = async () => {
  if (!inviteForm.value.friendId) {
    ElMessage.warning('请选择要邀请的好友');
    return;
  }
  
  if (!selectedContact.value || chatType.value !== 'group') {
    ElMessage.error('没有选中群组');
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

// 更新消息状态的辅助函数
const updateMessageStatus = (messageId, newStatus, realMessageId = null) => {
  console.log(`[ChatView] 更新消息状态: ${messageId} -> ${newStatus}`);
  
  // 查找并更新消息状态
  chatHistories.value.forEach((messages, key) => {
    const messageIndex = messages.findIndex(msg => msg.id === messageId);
    if (messageIndex !== -1) {
      const message = messages[messageIndex];
      message.status = newStatus;
      
      // 如果提供了真实消息ID，则更新消息ID并建立映射
      if (realMessageId && realMessageId !== messageId) {
        message.id = realMessageId;
        message.isOptimistic = false;
        messageStatusMap.value.set(messageId, realMessageId);
      }
      
      // 触发响应式更新
      chatHistories.value = new Map(chatHistories.value);
      console.log(`[ChatView] 消息状态已更新: ${messageId} -> ${newStatus}`);
      return true;
    }
  });
  
  // 如果消息确认成功，从待确认队列中移除
  if (newStatus === MESSAGE_STATUS.SENT || newStatus === MESSAGE_STATUS.DELIVERED) {
    pendingMessages.value.delete(messageId);
  }
  
  return false;
};

// 处理消息确认
const handleMessageAck = (ackData) => {
  console.log('[ChatView] 收到消息确认:', ackData);
  
  if (ackData.success) {
    updateMessageStatus(ackData.tempId, MESSAGE_STATUS.SENT, ackData.realId);
    
    // 如果有真实消息ID，说明消息已成功保存到数据库
    if (ackData.realId) {
      console.log(`[ChatView] 消息已确认保存: ${ackData.tempId} -> ${ackData.realId}`);
    }
  } else {
    updateMessageStatus(ackData.tempId, MESSAGE_STATUS.FAILED);
    ElMessage.error(`消息发送失败: ${ackData.error || '未知错误'}`);
    
    // 可以提供重试选项
    const pendingMsg = pendingMessages.value.get(ackData.tempId);
    if (pendingMsg && pendingMsg.retryCount < 3) {
      setTimeout(() => {
        offerRetry(ackData.tempId);
      }, 2000);
    }
  }
};

// 提供重试机制
const offerRetry = (messageId) => {
  const pendingMsg = pendingMessages.value.get(messageId);
  if (!pendingMsg) return;
  
  ElMessage({
    message: '消息发送失败，是否重试？',
    type: 'warning',
    showClose: true,
    duration: 0,
    customClass: 'retry-message',
    dangerouslyUseHTMLString: true,
    message: `
      <div>
        <p>消息发送失败，是否重试？</p>
        <p style="margin-top: 8px;">
          <button onclick="window.retryMessage('${messageId}')" style="margin-right: 8px; padding: 4px 8px; background: #409EFF; color: white; border: none; border-radius: 4px; cursor: pointer;">重试</button>
          <button onclick="window.cancelMessage('${messageId}')" style="padding: 4px 8px; background: #F56C6C; color: white; border: none; border-radius: 4px; cursor: pointer;">取消</button>
        </p>
      </div>
    `
  });
  
  // 提供全局重试函数
  window.retryMessage = (msgId) => {
    retryFailedMessage(msgId);
    ElMessage.closeAll();
  };
  
  window.cancelMessage = (msgId) => {
    pendingMessages.value.delete(msgId);
    updateMessageStatus(msgId, MESSAGE_STATUS.FAILED);
    ElMessage.closeAll();
  };
};

// 重试失败的消息
const retryFailedMessage = (messageId) => {
  const pendingMsg = pendingMessages.value.get(messageId);
  if (!pendingMsg) {
    console.warn('[ChatView] 找不到待重试的消息:', messageId);
    return;
  }
  
  pendingMsg.retryCount++;
  updateMessageStatus(messageId, MESSAGE_STATUS.SENDING);
  
  console.log(`[ChatView] 重试发送消息 (第${pendingMsg.retryCount}次):`, messageId);
  
  try {
    const { message, type, targetId, receiverName } = pendingMsg;
    
    if (type === 'group') {
      if (message.fileUrl) {
        stompClientInstance.sendPublicMessage(message.content, targetId, message);
      } else {
        stompClientInstance.sendPublicMessage(message.content, targetId);
      }
    } else if (type === 'private') {
      if (message.fileUrl) {
        stompClientInstance.sendPrivateMessage(targetId, message.content, receiverName, message);
      } else {
        stompClientInstance.sendPrivateMessage(targetId, message.content, receiverName);
      }
    }
    
    // 重新设置超时
    setTimeout(() => {
      if (pendingMessages.value.has(messageId)) {
        updateMessageStatus(messageId, MESSAGE_STATUS.FAILED);
        if (pendingMsg.retryCount < 3) {
          offerRetry(messageId);
        } else {
          ElMessage.error('消息发送失败，已达最大重试次数');
          pendingMessages.value.delete(messageId);
        }
      }
    }, 10000);
    
  } catch (error) {
    console.error('[ChatView] 重试发送消息失败:', error);
    updateMessageStatus(messageId, MESSAGE_STATUS.FAILED);
    if (pendingMsg.retryCount < 3) {
      setTimeout(() => offerRetry(messageId), 2000);
    }
  }
};
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100%; 
  width: 100%;
  overflow: hidden;
  background-color: var(--primary-color);
  box-shadow: var(--shadow-lg);
}

.main-chat-area {
  flex: 1;
  display: flex; 
  flex-direction: column; 
  overflow: hidden;
  background-color: var(--secondary-color);
  transition: all var(--transition-normal);
  position: relative; /* 添加相对定位以便侧边栏能够正确定位 */
}

/* Ensure child components (ChatArea/WelcomeScreen) fill the area */
.main-chat-area > * {
  flex: 1;
  min-height: 0; /* Important for flexbox shrinking */
}

.group-info-btn {
  margin-left: auto;
  color: #ffffff;
}

.group-detail-sidebar {
  position: absolute;
  top: 0;
  right: 0;
  width: 0;
  height: 100%;
  background-color: var(--secondary-color);
  border-left: 0px solid var(--border-color);
  overflow: hidden;
  transition: all 0.3s ease-in-out;
  box-shadow: -5px 0 15px rgba(0, 0, 0, 0.1);
  z-index: 10;
  display: flex;
  flex-direction: column;
}

.sidebar-open {
  width: 320px;
  border-left: 1px solid var(--border-color);
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--gradient-primary);
}

.sidebar-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
</style>