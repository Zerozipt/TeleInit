<template>
  <div class="chat-container">
    <!-- Sidebar -->
    <Sidebar
      :current-user="currentUser"
      :current-user-avatar="currentUserAvatar" 
      :friends="friends"
      :groups="groups"
      :selected-contact="selectedContact"
      :chat-type="chatType"
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
        />
        <WelcomeScreen v-else />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import stompClientInstance from '@/net/websocket';
import { getPrivateChatHistory, getGroupChatHistory } from '@/api/chatApi';
import { ElMessage } from 'element-plus';

// Import child components
import Sidebar from './components/chat/Sidebar.vue'; // Adjusted path
import ChatArea from './components/chat/ChatArea.vue'; // Adjusted path
import WelcomeScreen from './components/chat/WelcomeScreen.vue'; // Adjusted path

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

// 计算当前聊天消息
const currentChatMessages = computed(() => {
  if (!selectedContact.value) {
    return [];
  }
  const key = chatType.value === 'private' ? selectedContact.value.userId : selectedContact.value.groupId;
  const msgs = chatHistories.value.get(key) || [];
  return [...msgs].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
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

    stompClientInstance.on('onPrivateMessage', handlePrivateMessage);
    stompClientInstance.on('onPublicMessage', handlePublicMessage);
    stompClientInstance.on('onError', handleError);
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
    // stompClientInstance.off('onConnected', handleConnected);
    stompClientInstance.off('onError', handleError);
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
  
  if (Number(msg.senderId) === Number(currentUserId.value)) {
    return;
  }
  
  // 防止重复消息
  const cid = msg.senderId === Number(currentUserId.value) ? msg.receiverId : msg.senderId;
  const idKey = String(cid);
  const existing = chatHistories.value.get(idKey) || [];
  if (existing.length > 0) {
    const last = existing[existing.length - 1];
    if (last.senderId === msg.senderId && last.content === msg.content && Math.abs(new Date(last.timestamp) - new Date(msg.timestamp)) < 1000) {
      return;
    }
  }
  if (!chatHistories.value.has(idKey)) {
    chatHistories.value.set(idKey, []);
  }
  chatHistories.value.get(idKey).push(msg);
  chatHistories.value = new Map(chatHistories.value);
};

const handlePublicMessage = (message) => {
  console.log('[ChatView] 收到群聊消息:', message);
  
  const msg = normalizeMessage(message, 'group');
  
  if (Number(msg.senderId) === Number(currentUserId.value)) {
    return;
  }
  
  const idKey = String(msg.groupId);
  const existing = chatHistories.value.get(idKey) || [];
  if (existing.length > 0) {
    const last = existing[existing.length - 1];
    if (last.senderId === msg.senderId && last.content === msg.content && Math.abs(new Date(last.timestamp) - new Date(msg.timestamp)) < 1000) {
      return;
    }
  }
  
  if (!chatHistories.value.has(idKey)) {
    chatHistories.value.set(idKey, []);
  }
  chatHistories.value.get(idKey).push(msg);
  chatHistories.value = new Map(chatHistories.value);
};

const handleError = (error) => {
  console.error('[ChatView] WebSocket错误收到:', error);
  ElMessage.error(`WebSocket 错误: ${error}`);
};

// --- Event Handlers from Child Components ---
const handleSelectContact = (contact, type) => {
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
};

const handleSendMessage = (messageData) => {
  if (!selectedContact.value) return;
  const content = typeof messageData === 'string' ? messageData.trim() : messageData.content;
  if (!content) return;
  try {
    if (chatType.value === 'private') {
      const toId = selectedContact.value.userId;
      stompClientInstance.sendPrivateMessage(toId, content, selectedContact.value.username);
      // 添加本地私聊消息以供发送者渲染
      const localMsg = {
        id: Date.now(),
        content,
        timestamp: new Date().toISOString(),
        senderId: Number(currentUserId.value),
        sender: currentUser.value,
        receiverId: toId,
        groupId: null,
        type: 'CHAT'
      };
      // 更新历史记录
      const privKey = toId;
      const privHist = chatHistories.value.get(privKey) || [];
      privHist.push(localMsg);
      chatHistories.value.set(privKey, privHist);
      chatHistories.value = new Map(chatHistories.value);
    } else if (chatType.value === 'group') {
      const gid = selectedContact.value.groupId;
      stompClientInstance.sendPublicMessage(content, gid);
      // 添加本地群聊消息以供发送者渲染
      const localGrpMsg = {
        id: Date.now(),
        content,
        timestamp: new Date().toISOString(),
        senderId: Number(currentUserId.value),
        sender: currentUser.value,
        receiverId: null,
        groupId: gid,
        type: 'CHAT'
      };
      const grpKey = gid;
      const grpHist = chatHistories.value.get(grpKey) || [];
      grpHist.push(localGrpMsg);
      chatHistories.value.set(grpKey, grpHist);
      chatHistories.value = new Map(chatHistories.value);
    }
  } catch (e) {
    console.error('[ChatView] 发送消息失败:', e);
    ElMessage.error(`发送消息失败: ${e.message || '请检查连接'}`);
  }
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
    // 确定用于获取更早消息的锚点（例如，最旧消息的ID或时间戳）
    // **API限制**: 当前API调用不支持在特定点之前获取消息。
    // 我们假设API可能会返回更早的消息，但需要验证/API更改。
    const oldestMessageId = currentHistory.length > 0 ? currentHistory[0].id : null; 
    console.log(`[ChatView] 尝试加载更早的历史, 在消息ID: ${oldestMessageId}之前`);

    isLoadingHistory.value = true;
    try {
        let olderHistory = [];
        // **Placeholder**: Adjust API call when parameters for fetching older messages are available
        const fetchLimit = 50; // 或另一个合适的限制
        if (type === 'private') {
           //这里应该传入两个user的id
            olderHistory = await getPrivateChatHistory(currentUserId.value, contact.userId, fetchLimit);
        } else if (type === 'group') {
            // 假设API调用需要调整，例如getGroupChatHistory(contact.groupId, fetchLimit, oldestMessageId)
            olderHistory = await getGroupChatHistory(contact.groupId, fetchLimit);
        }

        console.log(`[ChatView] 获取更早的历史: ${key}:`, olderHistory.length);

        if (olderHistory && olderHistory.length > 0) {
            // 过滤掉任何已经存在的消息（如果API重叠发生，则很重要）
            const existingIds = new Set(currentHistory.map(m => m.id));
            const newMessages = olderHistory.filter(m => !existingIds.has(m.id));

            if (newMessages.length > 0) {
                const normalized = newMessages.map(raw => normalizeMessage(raw, chatType.value));
                chatHistories.value.set(key, [...normalized, ...currentHistory]);
            } else {
                console.log(`[ChatView] 没有找到新的更早的消息: ${key}.`);
                // 如果API返回的消息我们已经有了，假设没有更多的历史
                noMoreHistory.value = true;
            }
            
            // 如果返回的消息数量小于限制，假设没有更多的历史
            if (olderHistory.length < fetchLimit) {
                 console.log(`[ChatView] 获取的更早消息小于限制(${fetchLimit}), 假设没有更多的历史: ${key}.`);
                 noMoreHistory.value = true;
            }

        } else {
            console.log(`[ChatView] 没有返回更早的历史: ${key}.`);
            noMoreHistory.value = true; // 标记没有更多的历史要加载
        }

    } catch (error) {
        console.error('[ChatView] 通过API加载更早的聊天记录失败:', error);
        ElMessage.error(`加载更早的聊天记录失败: ${error.message || '请稍后重试'}`);
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
    sender: '',
    roomId: null,
    groupId: type === 'group' ? rawMsg.groupId : null,
    receiverId: type === 'private' ? Number(rawMsg.receiverId) : null
  };
  
  // 补齐 sender 用户名
  if (chatMsg.senderId === Number(currentUserId.value)) {
    chatMsg.sender = currentUser.value;
  } else {
    // 私聊消息，查找好友名称
    if (type === 'private') {
      // 查找新格式的好友
      const friend = friends.value.find(f => {
        // 判断当前用户是firstUser还是secondUser
        const isFirst = f.firstUserId === currentUserId.value.toString();
        const friendId = isFirst ? f.secondUserId : f.firstUserId;
        return friendId === chatMsg.senderId.toString();
      });
      
      if (friend) {
        // 判断当前用户是firstUser还是secondUser
        const isFirst = friend.firstUserId === currentUserId.value.toString();
        chatMsg.sender = isFirst ? friend.secondUsername : friend.firstUsername;
      } else {
        chatMsg.sender = '';
      }
    } 
    // 群聊消息，如果有 sender 则使用，否则尝试查找用户名
    else if (type === 'group') {
      if (rawMsg.sender) {
        chatMsg.sender = rawMsg.sender;
      } else {
        // 查找新格式的好友以匹配用户名
        const friend = friends.value.find(f => {
          const isFirst = f.firstUserId === currentUserId.value.toString();
          const friendId = isFirst ? f.secondUserId : f.firstUserId;
          return friendId === chatMsg.senderId.toString();
        });
        
        if (friend) {
          const isFirst = friend.firstUserId === currentUserId.value.toString();
          chatMsg.sender = isFirst ? friend.secondUsername : friend.firstUsername;
        } else {
          chatMsg.sender = '未知用户';
        }
      }
    }
  }
  return chatMsg;
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
}

/* Ensure child components (ChatArea/WelcomeScreen) fill the area */
.main-chat-area > * {
  flex: 1;
  min-height: 0; /* Important for flexbox shrinking */
}

/* Adjust paths if components are moved */

</style>