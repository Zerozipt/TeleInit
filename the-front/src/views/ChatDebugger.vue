<template>
  <div class="chat-debugger">
    <el-card class="control-panel">
      <template #header>
        <div class="card-header">
          <span><el-icon><Connection /></el-icon> WebSocket 调试控制台</span>
          <div>
            <el-button type="primary" @click="handleConnect" :disabled="isConnected || isConnecting" :loading="isConnecting">
              <el-icon><Link /></el-icon> 连接
            </el-button>
            <el-button type="danger" @click="handleDisconnect" :disabled="!isConnected">
              <el-icon><CloseBold /></el-icon> 断开
            </el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="connectionStatus" :type="connectionStatusType" :title="connectionStatus" show-icon />

      <div class="connection-info" v-if="isConnected">
        <el-tag type="success">已连接</el-tag>
        <span>用户: <el-tag>{{ currentUser }}</el-tag></span>
      </div>
    </el-card>

    <div class="chat-container">
      <!-- 公共聊天 -->
      <el-card class="chat-card">
        <template #header>
          <div class="card-header">
            <span><el-icon><ChatDotRound /></el-icon> 公共聊天 (频道: /topic/public/general)</span>
          </div>
        </template>

        <div class="message-list" ref="publicChatList">
          <el-scrollbar height="300px" ref="publicScrollbar">
            <div v-if="publicMessages.length === 0" class="empty-messages">
              <el-empty description="暂无公共消息" />
            </div>
            <div v-else>
              <div v-for="(msg, index) in publicMessages" :key="'pub-' + index" class="message-item" :class="{'my-message': msg.sender === currentUser}">
                <div class="message-header">
                  <el-tag size="small" :type="msg.sender === currentUser ? 'primary' : 'info'">{{ msg.sender }}</el-tag>
                  <el-tag size="small" type="info">{{ formatTimestamp(msg.timestamp) }}</el-tag>
                </div>
                <div class="message-content">{{ msg.content }}</div>
              </div>
            </div>
          </el-scrollbar>
        </div>

        <div class="message-input">
          <el-input v-model="publicMessageInput" placeholder="输入公共消息" :disabled="!isConnected" @keyup.enter="sendPublicMessage">
            <template #append>
              <el-button @click="sendPublicMessage" :disabled="!isConnected || !publicMessageInput.trim()">
                <el-icon><Position /></el-icon> 发送
              </el-button>
            </template>
          </el-input>
        </div>
      </el-card>

      <!-- 私人聊天 -->
      <el-card class="chat-card">
        <template #header>
          <div class="card-header">
            <span><el-icon><ChatLineRound /></el-icon> 私人聊天 (频道: /user/queue/private)</span>
          </div>
        </template>

        <div class="message-list" ref="privateChatList">
          <el-scrollbar height="300px" ref="privateScrollbar">
            <div v-if="privateMessages.length === 0" class="empty-messages">
              <el-empty description="暂无私聊消息" />
            </div>
            <div v-else>
              <div v-for="(msg, index) in privateMessages" :key="'priv-' + index" class="message-item" :class="{'my-message': msg.fromUser === currentUser}">
                 <div class="message-header">
                  <div class="private-message-users">
                    <el-tag size="small" :type="msg.fromUser === currentUser ? 'primary' : 'success'">{{ msg.fromUser }}</el-tag>
                    <el-icon><Right /></el-icon>
                    <el-tag size="small" type="warning">{{ msg.toUser }}</el-tag>
                  </div>
                   <el-tag size="small" type="info">{{ formatTimestamp(msg.timestamp) }}</el-tag>
                 </div>
                <div class="message-content">{{ msg.content }}</div>
              </div>
            </div>
          </el-scrollbar>
        </div>

        <div class="message-input">
          <el-input v-model="targetUserInput" placeholder="接收用户名" :disabled="!isConnected" style="margin-bottom: 10px" />
          <el-input v-model="privateMessageInput" placeholder="输入私聊消息" :disabled="!isConnected" @keyup.enter="sendPrivateMessage">
            <template #append>
              <el-button @click="sendPrivateMessage" :disabled="!isConnected || !privateMessageInput.trim() || !targetUserInput.trim()">
                <el-icon><Position /></el-icon> 发送
              </el-button>
            </template>
          </el-input>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup> // 使用 setup script 语法
import { ref, onBeforeUnmount, nextTick, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Connection, Link, CloseBold, ChatDotRound, ChatLineRound, Position, Right } from '@element-plus/icons-vue';
import stompClient from '@/net/websocket'; // 导入封装好的客户端实例

// --- Refs ---
const isConnected = ref(stompClient.isConnected); // 直接引用 StompClientWrapper 中的 ref
const currentUser = ref(stompClient.currentUser); // 直接引用 StompClientWrapper 中的 ref
const isConnecting = ref(false);
const connectionStatus = ref('');
const connectionStatusType = ref('info'); // info, success, warning, error

const publicMessageInput = ref('');
const privateMessageInput = ref('');
const targetUserInput = ref('');

const publicMessages = ref([]);
const privateMessages = ref([]);

// Scrollbar refs
const publicScrollbar = ref();
const privateScrollbar = ref();
const publicChatList = ref(); // Ref for the message list container
const privateChatList = ref(); // Ref for the message list container

// --- Computed / Watchers ---
watch(publicMessages, async () => {
  await nextTick();
  if (publicScrollbar.value) {
    publicScrollbar.value.setScrollTop(publicChatList.value.scrollHeight);
  }
}, { deep: true });

watch(privateMessages, async () => {
  await nextTick();
  if (privateScrollbar.value) {
    privateScrollbar.value.setScrollTop(privateChatList.value.scrollHeight);
  }
}, { deep: true });


// --- Methods ---
const formatTimestamp = (timestamp) => {
  if (!timestamp) return '';
  try {
    // 假设后端传来的是 Date 对象或 ISO 字符串
    return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch (e) {
    return timestamp; // 返回原始值如果格式化失败
  }
};

const handleConnect = async () => {
  connectionStatus.value = '正在连接...';
  connectionStatusType.value = 'info';
  isConnecting.value = true;

  try {
    // 从 localStorage 获取 Token
    const tokenData = localStorage.getItem('authorize');
    if (!tokenData) {
      throw new Error('未找到授权令牌，请先登录');
    }

    let jwt = '';
    try {
        // 尝试解析可能存在的 JSON 结构
        const tokenObj = JSON.parse(tokenData);
        jwt = tokenObj.token || (typeof tokenData === 'string' ? tokenData : ''); // 兼容直接存储 token 字符串
    } catch (e) {
        // 如果解析失败，假定它就是 token 字符串
        jwt = typeof tokenData === 'string' ? tokenData : '';
    }


    if (!jwt) {
      throw new Error('JWT 令牌为空或格式错误');
    }

    // 调用封装好的连接方法
    await stompClient.connect(jwt);
    // 连接成功的回调已在 websocket.js 中处理 isConnected 和 currentUser

  } catch (error) {
    console.error('连接处理失败:', error);
    connectionStatus.value = `连接失败: ${error.message || '未知错误'}`;
    connectionStatusType.value = 'error';
    ElMessage.error(connectionStatus.value);
  } finally {
      isConnecting.value = false;
  }
};

const handleDisconnect = () => {
  stompClient.disconnect();
  // 断开连接的回调已在 websocket.js 中处理 isConnected 和 currentUser
  // 可能需要手动清除消息列表或保留
   // publicMessages.value = [];
   // privateMessages.value = [];
};

const sendPublicMessage = () => {
  if (isConnected.value && publicMessageInput.value.trim()) {
    stompClient.sendPublicMessage(publicMessageInput.value);
    publicMessageInput.value = '';
  }
};

const sendPrivateMessage = () => {
  if (isConnected.value && privateMessageInput.value.trim() && targetUserInput.value.trim()) {
      if (targetUserInput.value === currentUser.value) {
          ElMessage.warning('不能给自己发送私聊消息');
          return;
      }
    stompClient.sendPrivateMessage(targetUserInput.value, privateMessageInput.value);
    // 不再手动添加到列表，等待从服务器接收回显
    privateMessageInput.value = '';
    // 可选：不清空 targetUser，方便连续发送
  }
};

// --- Lifecycle and Event Handling ---

// 注册事件回调
stompClient.on('onConnected', (user) => {
  isConnected.value = true; // 确保状态同步
  currentUser.value = user; // 确保状态同步
  connectionStatus.value = `连接成功! 用户: ${user}`;
  connectionStatusType.value = 'success';
  ElMessage.success('WebSocket 连接成功！');
  // 清空旧消息（如果需要）
  publicMessages.value = [];
  privateMessages.value = [];
});

stompClient.on('onDisconnected', () => {
  isConnected.value = false;
  currentUser.value = '';
  connectionStatus.value = '已断开连接';
  connectionStatusType.value = 'info';
  ElMessage.info('WebSocket 已断开');
});

stompClient.on('onError', (errorMessage) => {
  isConnected.value = false; // 可能因错误断开
  currentUser.value = '';
  connectionStatus.value = `连接错误: ${errorMessage}`;
  connectionStatusType.value = 'error';
  ElMessage.error(`WebSocket 错误: ${errorMessage}`);
});

stompClient.on('onPublicMessage', (message) => {
  publicMessages.value.push(message);
});

stompClient.on('onPrivateMessage', (message) => {
  privateMessages.value.push(message);
});

// 组件卸载时断开连接
onBeforeUnmount(() => {
  handleDisconnect();
  // 清理回调，防止内存泄漏（如果 StompClientWrapper 不是单例或需要动态创建/销毁）
  // stompClient.callbacks = { ... }; // 如果需要完全重置
});

</script>

<style scoped>
.chat-debugger {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.control-panel {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.connection-info {
  margin-top: 15px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-container {
  display: grid; /* 使用 Grid 布局 */
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr)); /* 响应式列 */
  gap: 20px;
}

.chat-card {
  display: flex;
  flex-direction: column; /* 让卡片内容垂直排列 */
}

.message-list {
  flex-grow: 1; /* 占据剩余空间 */
  margin-bottom: 15px;
  overflow: hidden; /* 配合 Scrollbar */
  border: 1px solid #ebeef5;
  border-radius: 4px;
}

.message-item {
  margin-bottom: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  background-color: #f4f4f5; /* 默认背景 */
  display: flex;
  flex-direction: column;
  word-wrap: break-word; /* 防止长单词溢出 */
}

.message-item.my-message {
  background-color: #e1f3d8; /* 自己发送的消息背景 */
  align-self: flex-end; /* 如果想靠右显示可以配合容器设置 */
}

.message-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
    flex-wrap: wrap; /* 允许换行 */
    gap: 5px;
}

.message-content {
  margin-top: 4px;
}

.private-message-users {
  display: flex;
  align-items: center;
  gap: 5px;
}

.message-input {
  margin-top: auto; /* 将输入框推到底部 */
  padding-top: 10px; /* 添加一点间距 */
}

.empty-messages {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 280px; /* 调整高度以适应 Scrollbar */
  color: #909399;
}

/* 滚动条样式 (可选) */
:deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}
</style>