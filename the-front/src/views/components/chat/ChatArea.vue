<template>
  <div class="chat-area" v-if="contact">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <div class="contact-title">
        {{ type === 'private' ? contact.username : contact.groupName }}
      </div>
      <div class="contact-info">
        {{ type === 'private' ? '私聊' : '群聊' }}
      </div>
    </div>

    <!-- 聊天消息列表 -->
    <div class="chat-messages" ref="messagesContainer" @scroll="handleScroll">
      <!-- Load More / History Status -->
      <div class="history-loader">
        <el-button 
          type="primary" 
          link 
          size="small" 
          @click="triggerLoadMore"
          :loading="isLoadingHistory"
          :disabled="noMoreHistory || isLoadingHistory"
        >
          {{ isLoadingHistory ? '加载中...' : (noMoreHistory ? '没有更多记录了' : '加载更早记录') }}
        </el-button>
      </div>

      <!-- Message Items -->
      <div
        v-for="(message, index) in messages"
        :key="message.id || index" 
        class="message-wrapper"
      >
        <div class="message-item"
            :class="{ 'message-self': isSelfMessage(message) }">
          <el-avatar
            class="message-avatar"
            size="small"
            :class="{ 'avatar-self': isSelfMessage(message) }"
          >
            {{ getAvatarText(message) }}
          </el-avatar>
          <div class="message-content">
            <div class="message-sender" v-if="type === 'group' && !isSelfMessage(message)">
              {{ message.sender || '未知用户' }}
            </div>
            <div class="message-bubble">
              {{ message.content }}
            </div>
          </div>
        </div>
        <!-- 将时间戳放在外层容器中 -->
        <div class="message-time" :class="{ 'time-self': isSelfMessage(message) }">
          {{ formatTime(message.timestamp) }}
        </div>
      </div>

      <!-- Empty Chat Placeholder -->
      <div v-if="messages.length === 0 && !isLoadingHistory" class="empty-chat">
        <el-icon><el-icon-chat-round /></el-icon>
        <p>{{ type === 'private' ? '开始与好友聊天吧' : '开始群聊吧' }}</p>
      </div>
    </div>

    <!-- 聊天输入框 -->
    <div class="chat-input-area">
      <el-input
        v-model="messageText"
        type="textarea"
        :rows="3"
        placeholder="输入消息..."
        resize="none"
        @keyup.enter.native.exact="handleSendMessage"
      />
      <div class="send-button">
        <el-button type="primary" :disabled="!messageText.trim()" @click="handleSendMessage">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onUpdated } from 'vue';
import { ChatRound as ElIconChatRound } from '@element-plus/icons-vue';

const props = defineProps({
  contact: Object, 
  type: String, 
  messages: Array, 
  currentUserId: [String, Number], 
  currentUser: String, 
  // Props for history loading
  isLoadingHistory: Boolean,
  noMoreHistory: Boolean
});

const emit = defineEmits(['send-message', 'load-more-history']);

const messageText = ref('');
const messagesContainer = ref(null);
// Flag to prevent scroll-to-bottom when user is viewing older messages
const shouldScrollToBottom = ref(true); 
// Store scroll position before loading more history
let scrollHeightBeforeLoad = 0;
let scrollTopBeforeLoad = 0;

const formatTime = (timestamp) => {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  return date.toLocaleString('zh-CN');  // 显示完整的日期和时间
};

const scrollToBottom = (force = false) => {
  if (messagesContainer.value && (shouldScrollToBottom.value || force)) {
    nextTick(() => {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
      console.log('[ChatArea] Scrolled to bottom');
    });
  }
};

// 判断消息是否为自己发送
const isSelfMessage = (message) => {
  return message.senderId === Number(props.currentUserId);
};

// 获取头像文本 (取发送者首字母)
const getAvatarText = (message) => {
  return (message.sender || '').substring(0, 1).toUpperCase();
};

const handleSendMessage = () => {
  if (!messageText.value.trim()) return;
  // 创建包含更多信息的消息对象
  const messageData = {
    content: messageText.value,
    timestamp: new Date().toISOString(),
    // 同时包含新旧格式的ID
    fromUserId: props.currentUserId,
    senderId: props.currentUserId,
    // 同时包含用户名信息
    sender: props.currentUser,
    fromUser: props.currentUser
  };
  
  emit('send-message', messageData);
  messageText.value = '';
  // Force scroll to bottom after sending a message
  shouldScrollToBottom.value = true; 
  scrollToBottom(true);
};

// Function to trigger the load-more-history event
const triggerLoadMore = () => {
    if (!props.isLoadingHistory && !props.noMoreHistory) {
        console.log('[ChatArea] Triggering load more history');
        // Store scroll state before loading starts
        scrollHeightBeforeLoad = messagesContainer.value.scrollHeight;
        scrollTopBeforeLoad = messagesContainer.value.scrollTop;
        shouldScrollToBottom.value = false; // Prevent auto-scroll down
        emit('load-more-history');
    }
};

// Optional: Detect scroll to top to load more history
// const handleScroll = (event) => {
//   const container = event.target;
//   if (container.scrollTop === 0 && !props.isLoadingHistory && !props.noMoreHistory) {
//     console.log('[ChatArea] Scrolled to top, triggering load more history');
//     triggerLoadMore();
//   }
// };

// Watch for new messages and scroll down only if user is near the bottom
watch(() => props.messages, (newMessages, oldMessages) => {
    if (!messagesContainer.value) return;

    const newLength = newMessages?.length || 0;
    const oldLength = oldMessages?.length || 0;

    if (newLength > oldLength) { // If messages were added
        // Message added was likely prepended (history load)
        if (shouldScrollToBottom.value === false && newMessages[0]?.id !== oldMessages[0]?.id) {
            console.log('[ChatArea] Older messages loaded, restoring scroll position.');
            nextTick(() => {
                // Restore scroll position based on added height
                const newScrollHeight = messagesContainer.value.scrollHeight;
                messagesContainer.value.scrollTop = scrollTopBeforeLoad + (newScrollHeight - scrollHeightBeforeLoad);
                console.log('[ChatArea] Scroll restored to:', messagesContainer.value.scrollTop);
                // Reset the flag after restoring scroll
                // shouldScrollToBottom.value = true; // Keep it false until user scrolls down
            });
        } else { // Message added was likely appended (new message)
            console.log('[ChatArea] New message received, checking scroll position.');
            // Check if user was near the bottom before the update
            const threshold = 100; // Pixels from bottom
            const nearBottom = messagesContainer.value.scrollHeight - messagesContainer.value.scrollTop - messagesContainer.value.clientHeight < threshold;
            
            if (nearBottom) {
                console.log('[ChatArea] User near bottom, scrolling down.');
                scrollToBottom(true); // Force scroll if near bottom
            } else {
                console.log('[ChatArea] User not near bottom, not auto-scrolling.');
            }
             shouldScrollToBottom.value = true; // Reset flag after receiving new message
        }
    }
}, { deep: true });

// Watch for contact change to scroll to bottom when switching chats
watch(() => props.contact, () => {
    console.log('[ChatArea] Contact changed, scrolling to bottom.');
    shouldScrollToBottom.value = true; // Reset scroll behavior
    nextTick(() => scrollToBottom(true)); // Use nextTick to ensure DOM is updated
});

</script>

<style scoped>
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background-color: var(--primary-color);
}

.chat-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-color);
  background-color: var(--secondary-color);
  flex-shrink: 0;
  background: var(--gradient-primary);
}

.contact-title {
  font-weight: bold;
  font-size: 18px;
  color: var(--text-color);
}

.contact-info {
  color: var(--text-secondary);
  font-size: 14px;
  margin-top: 5px;
}

.chat-messages {
  flex: 1; 
  overflow-y: auto; 
  padding: 16px 20px;
  background-color: var(--primary-color);
  background-image: 
    radial-gradient(circle at 15% 20%, rgba(0, 100, 150, 0.05) 0%, transparent 50%),
    radial-gradient(circle at 85% 60%, rgba(0, 100, 150, 0.05) 0%, transparent 50%);
}

.history-loader {
  display: flex;
  justify-content: center;
  padding: 10px 0;
  margin-bottom: 10px;
  opacity: 0.8;
}

/* 添加加载更早记录按钮的样式 */
.history-loader .el-button {
  color: var(--accent-color) !important; /* 设置为强调色，或其他高对比度颜色 */
  font-weight: bold !important; /* 加粗文字增加对比度 */
  background-color: transparent !important; /* 确保背景透明 */
}

/* 针对禁用状态的样式 */
.history-loader .el-button.is-disabled {
  color: var(--text-secondary) !important; /* 禁用状态使用次要文本颜色 */
  opacity: 0.7 !important;
}

/* 消息包装器 - 包含消息项和时间戳 */
.message-wrapper {
  display: flex;
  flex-direction: column;
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease;
}

/* 消息项 - 包含头像和内容 */
.message-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 4px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-self {
  flex-direction: row-reverse;
}

.message-avatar {
  margin: 0 8px;
  flex-shrink: 0;
}

.avatar-self {
  background-color: var(--accent-color) !important;
}

.message-content {
  display: flex;
  flex-direction: column;
  max-width: 70%;
}

.message-sender {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
  padding-left: 12px;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: var(--border-radius-md);
  box-shadow: var(--shadow-sm);
  position: relative;
  background-color: var(--bubble-other);
  color: var(--text-color);
  word-break: break-word;
  max-width: 100%;
  display: inline-block;
}

.message-self .message-bubble {
  background: linear-gradient(135deg, #0076a8 0%, #005980 100%);
  color: white;
  border-radius: 16px 4px 16px 16px;
}

.message-item:not(.message-self) .message-bubble {
  border-radius: 4px 16px 16px 16px;
}

/* 时间戳样式 */
.message-time {
  font-size: 11px;
  color: var(--text-secondary);
  opacity: 0.8;
  align-self: flex-start; /* 默认时间戳靠左 */
  margin-left: 46px; /* 与头像和消息内容对齐 */
}

/* 自己发送的消息时间戳靠右 */
.time-self {
  align-self: flex-end;
  margin-right: 46px; /* 与右侧对齐 */
}

.chat-input-area {
  padding: 16px;
  border-top: 1px solid var(--border-color);
  background-color: var(--secondary-color);
  display: flex;
  flex-direction: column;
}

/* 修正Element Plus文本域样式 */
.chat-input-area :deep(.el-textarea__inner) {
  background-color: var(--tertiary-color) !important;
  border-color: var(--border-color) !important;
  box-shadow: none !important;
  color: var(--text-color) !important;
  border-radius: var(--border-radius-md);
  padding: 12px;
  min-height: 80px;
  transition: all var(--transition-fast);
}

.chat-input-area :deep(.el-textarea__inner:focus) {
  border-color: var(--accent-color) !important;
  box-shadow: 0 0 0 1px var(--accent-color) inset !important;
}

.send-button {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.send-button .el-button {
  padding: 12px 24px;
  font-weight: 500;
  border-radius: var(--border-radius-md);
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-secondary);
  opacity: 0.7;
  user-select: none;
}

.empty-chat i {
  font-size: 48px;
  margin-bottom: 16px;
  color: var(--accent-color);
}

/* Dark theme overrides for el-textarea */
:deep(.el-textarea__inner) {
  background-color: var(--background-light) !important;
  color: var(--text-color) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: var(--border-radius-md);
  transition: all var(--transition-fast);
}

:deep(.el-textarea__inner:focus) {
  border-color: var(--accent-color) !important;
  box-shadow: 0 0 0 2px rgba(0, 184, 255, 0.1);
}

/* 活跃效果 */
.message-bubble:hover {
  box-shadow: var(--shadow-md);
}

.message-self .message-bubble:hover {
  box-shadow: 0 0 8px rgba(0, 184, 255, 0.4);
}
</style> 