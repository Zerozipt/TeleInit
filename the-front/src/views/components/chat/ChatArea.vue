<template>
  <div class="chat-area" v-if="contact">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <div class="contact-info">
        <div class="avatar">
          <!-- 头像容器组件 -->
          <el-avatar :size="40" :src="contact.avatar">
            <!-- 如果是私聊，使用对方用户名首字母，如果是群聊使用群名首字母 -->
            {{ getDisplayName().charAt(0).toUpperCase() }}
          </el-avatar>
        </div>
        <div class="name-status">
          <div class="name">{{ getDisplayName() }}</div>
          <div class="status" v-if="type === 'private'">{{ getStatus() }}</div>
          <div class="status" v-else-if="type === 'group'">({{ getGroupMemberCount() }}人)</div>
        </div>
      </div>
      <div class="header-actions">
        <!-- 提供槽位以便父组件放置自定义操作按钮，例如群聊详情按钮 -->
        <slot name="header-actions"></slot>
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
              {{ message.sender || '' }}
            </div>
            <div class="message-bubble"
              :class="{ 'file-message': isFileMessage(message) }">
              <!-- 文件消息渲染 -->
              <template v-if="isFileMessage(message)">
                <div class="file-container">
                  <!-- 根据文件类型显示不同图标 -->
                  <div class="file-icon">
                    <i class="el-icon" v-if="getFileType(message) === 'IMAGE'">
                      <el-icon><Picture /></el-icon>
                    </i>
                    <i class="el-icon" v-else-if="getFileType(message) === 'VIDEO'">
                      <el-icon><VideoPlay /></el-icon>
                    </i>
                    <i class="el-icon" v-else-if="getFileType(message) === 'AUDIO'">
                      <el-icon><Headset /></el-icon>
                    </i>
                    <i class="el-icon" v-else>
                      <el-icon><Document /></el-icon>
                    </i>
                  </div>
                  <div class="file-info">
                    <div class="file-name">{{ getFileName(message) }}</div>
                    <div class="file-size" v-if="message.fileSize">{{ formatFileSize(message.fileSize) }}</div>
                  </div>
                  <a :href="message.fileUrl" target="_blank" class="download-btn" 
                     :download="getFileName(message)" @click.prevent="handleFileDownload(message)">
                    <el-icon><Download /></el-icon>
                  </a>
                </div>
              </template>
              <!-- 普通文本消息渲染 -->
              <template v-else>
                {{ message.content }}
              </template>
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
      <div class="input-toolbar">
        <el-button type="text" @click="toggleEmojiPicker" ref="emojiButtonRef">
          😀
        </el-button>
        <el-button type="text" @click="handleFileButtonClick">📎</el-button>
        <input type="file" ref="fileInputRef" style="display:none" @change="handleFileChange" />
        <div v-if="showEmojiPicker" class="emoji-picker-container" ref="emojiPickerRef">
          <Picker :data="emojiIndex" @select="onEmojiSelect" :emojiSize="20" :perLine="8" />
        </div>
      </div>
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
import { ref, watch, nextTick, onMounted, onUnmounted, computed } from 'vue';
import { ChatRound as ElIconChatRound, Picture, VideoPlay, Headset, Document, Download } from '@element-plus/icons-vue';
import data from 'emoji-mart-vue-fast/data/all.json';
import { Picker, EmojiIndex } from 'emoji-mart-vue-fast/src';
import 'emoji-mart-vue-fast/css/emoji-mart.css';
import { uploadFile } from '@/api/fileApi';

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
const showEmojiPicker = ref(false);
const emojiPickerRef = ref(null);
const emojiButtonRef = ref(null);
const fileInputRef = ref(null);
const toggleEmojiPicker = () => { showEmojiPicker.value = !showEmojiPicker.value; };
const onEmojiSelect = (emoji) => { messageText.value += emoji.native; };
const emojiIndex = new EmojiIndex(data);
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

// 获取显示名称
const getDisplayName = () => {
  if (props.type === 'private') {
    return props.contact.username || '好友';
  } else {
    return props.contact.groupName || '群聊';
  }
};

// 获取私聊状态
const getStatus = () => {
  if (props.type !== 'private') return '';
  
  // 这里可以根据实际情况添加在线状态判断
  // 如果有在线状态字段，可以根据该字段返回对应的文本
  return props.contact.online ? '在线' : '离线';
};

// 获取群成员数量
const getGroupMemberCount = () => {
  if (props.type !== 'group') return '';
  
  // 如果contact对象中有memberCount字段，则使用该字段
  return props.contact.memberCount || '未知';
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

// 功能是触发加载更早记录
const triggerLoadMore = () => {
    if (!props.isLoadingHistory && !props.noMoreHistory) {
        console.log('[ChatArea] Triggering load more history');
        // 在加载开始之前存储滚动状态
        scrollHeightBeforeLoad = messagesContainer.value.scrollHeight;
        scrollTopBeforeLoad = messagesContainer.value.scrollTop;
        shouldScrollToBottom.value = false; // 防止自动向下滚动
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

// 监听新消息并仅在用户接近底部时向下滚动
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

// 监听联系人变化以在切换聊天时向下滚动
watch(() => props.contact, () => {
    console.log('[ChatArea] Contact changed, scrolling to bottom.');
    shouldScrollToBottom.value = true; // Reset scroll behavior
    nextTick(() => scrollToBottom(true)); // Use nextTick to ensure DOM is updated
});

// 点击外部关闭 emoji 选择器
const handleClickOutside = (e) => {
  if (showEmojiPicker.value &&
      emojiPickerRef.value && !emojiPickerRef.value.contains(e.target) &&
      emojiButtonRef.value && emojiButtonRef.value.$el && !emojiButtonRef.value.$el.contains(e.target)) {
    showEmojiPicker.value = false;
  }
};

// 触发文件选择
const handleFileButtonClick = () => {
  fileInputRef.value && fileInputRef.value.click();
};

// 判断消息是否为文件消息
const isFileMessage = (message) => {
  // 判断逻辑：1. 有fileUrl字段 2. messageType为FILE/IMAGE/VIDEO/AUDIO之一
  return (message.fileUrl && message.fileUrl.trim().length > 0) || 
         (message.messageType && ['FILE', 'IMAGE', 'VIDEO', 'AUDIO'].includes(message.messageType.toUpperCase()));
};

// 获取文件类型
const getFileType = (message) => {
  // 优先使用messageType
  if (message.messageType) {
    return message.messageType.toUpperCase();
  }
  
  // 如果没有messageType但有fileType，尝试推断
  if (message.fileType) {
    if (message.fileType.startsWith('image/')) return 'IMAGE';
    if (message.fileType.startsWith('video/')) return 'VIDEO'; 
    if (message.fileType.startsWith('audio/')) return 'AUDIO';
    return 'FILE';
  }
  
  // 如果没有messageType也没有fileType，但有fileUrl，检查文件扩展名
  if (message.fileUrl) {
    const url = message.fileUrl.toLowerCase();
    if (url.match(/\.(jpg|jpeg|png|gif|webp|svg)(\?|$)/)) return 'IMAGE';
    if (url.match(/\.(mp4|avi|mov|wmv|flv|mkv|webm)(\?|$)/)) return 'VIDEO';
    if (url.match(/\.(mp3|wav|ogg|flac|aac)(\?|$)/)) return 'AUDIO';
    return 'FILE';
  }
  
  return 'FILE'; // 默认文件类型
};

// 获取文件名
const getFileName = (message) => {
  // 优先使用fileName字段
  if (message.fileName) return message.fileName;
  
  // 如果没有fileName但有fileUrl，从URL中提取
  if (message.fileUrl) {
    // 移除查询参数
    const urlWithoutParams = message.fileUrl.split('?')[0];
    // 获取URL最后一部分
    const parts = urlWithoutParams.split('/');
    return parts[parts.length - 1] || '未知文件';
  }
  
  // 如果没有文件名也没有URL，使用内容字段，或返回默认值
  return message.content || '文件';
};

// 格式化文件大小
const formatFileSize = (size) => {
  if (!size) return '';
  
  const units = ['B', 'KB', 'MB', 'GB'];
  let sizeNum = parseInt(size, 10);
  let unitIndex = 0;
  
  while (sizeNum >= 1024 && unitIndex < units.length - 1) {
    sizeNum /= 1024;
    unitIndex++;
  }
  
  return `${sizeNum.toFixed(1)} ${units[unitIndex]}`;
};

// 处理文件下载
const handleFileDownload = (message) => {
  if (!message.fileUrl) return;
  
  console.log('[ChatArea] 开始下载文件:', message.fileName, '，URL:', message.fileUrl);
  
  // 获取认证token以处理可能需要认证的下载
  let authHeader = '';
  try {
    const authData = localStorage.getItem('authorize');
    if (authData) {
      const parsedAuth = JSON.parse(authData);
      if (parsedAuth?.token) {
        authHeader = `Bearer ${parsedAuth.token}`;
      }
    }
  } catch (err) {
    console.error('[ChatArea] 获取认证信息失败:', err);
  }
  
  // 使用fetch API进行下载，确保设置正确的请求头
  fetch(message.fileUrl, {
    method: 'GET',
    headers: {
      ...(authHeader && { 'Authorization': authHeader })
    }
  })
  .then(response => {
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status} ${response.statusText}`);
    }
    return response.blob();
  })
  .then(blob => {
    // 创建一个临时URL
    const url = window.URL.createObjectURL(blob);
    
    // 创建隐藏的a标签并模拟点击
    const link = document.createElement('a');
    link.href = url;
    link.download = getFileName(message);
    document.body.appendChild(link);
    link.click();
    
    // 清理
    window.URL.revokeObjectURL(url);
    document.body.removeChild(link);
    console.log('[ChatArea] 文件下载完成:', message.fileName);
  })
  .catch(error => {
    console.error('[ChatArea] 文件下载失败:', error);
  });
};

// 处理文件选择并上传
const handleFileChange = async (e) => {
  console.log('[ChatArea] handleFileChange 触发, 文件: ', e.target.files);
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  try {
    console.log('[ChatArea] 开始上传文件', file.name);
    const response = await uploadFile(file);
    console.log('[ChatArea] fileApi 返回:', response);
    
    // 构建更完整的文件消息对象
    const fileMessage = {
      content: file.name, // 文件名作为内容
      timestamp: new Date().toISOString(),
      fromUserId: props.currentUserId,
      senderId: props.currentUserId,
      sender: props.currentUser,
      fileUrl: response.fileUrl,
      fileName: response.fileName || file.name,
      fileType: response.fileType || file.type,
      fileSize: response.fileSize || file.size,
      messageType: response.messageType || getMessageTypeFromFile(file)
    };
    emit('send-message', fileMessage);
  } catch (err) {
    console.error('文件上传或发送失败', err);
  } finally {
    e.target.value = '';
  }
};

// 根据文件类型判断消息类型
const getMessageTypeFromFile = (file) => {
  if (file.type.startsWith('image/')) return 'IMAGE';
  if (file.type.startsWith('video/')) return 'VIDEO';
  if (file.type.startsWith('audio/')) return 'AUDIO';
  return 'FILE';
};

onMounted(() => document.addEventListener('click', handleClickOutside));
onUnmounted(() => document.removeEventListener('click', handleClickOutside));

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
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.contact-info {
  color: var(--text-secondary);
  font-size: 14px;
  display: flex;
  align-items: center;
}

.avatar {
  margin-right: 16px;
}

.name-status {
  display: flex;
  flex-direction: column;
}

.name {
  font-weight: bold;
  font-size: 18px;
  color: var(--text-color);
}

.status {
  margin-top: 5px;
}

.header-actions {
  display: flex;
  align-items: center;
  margin-left: auto;
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
  position: relative;
  padding: 16px;
  border-top: 1px solid var(--border-color);
  background-color: var(--secondary-color);
  display: flex;
  flex-direction: column;
}

/* Emoji 选择器工具栏 */
.input-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

/* 表情选择器弹出容器 */
.emoji-picker-container {
  position: absolute;
  bottom: 100%;
  left: 0;
  margin-bottom: 8px;
  width: 100%;
  max-width: 320px;
  max-height: 250px;
  overflow: hidden;
  background-color: #2d3035;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  padding: 0;
  border-radius: 8px;
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

/* 黑色主题样式覆盖 */
.emoji-picker-container :deep(.emoji-mart) {
  background-color: #2d3035;
  color: #f0f0f0;
  border-color: #444;
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.4);
  width: 100%;
  height: auto;
  max-height: 350px;
  border: none;
  margin: 0;
  padding: 0;
  border-radius: 0;
}

/* 修复右侧滚动条区域 */
.emoji-picker-container :deep(.emoji-mart-scroll) {
  background-color: #2d3035;
  margin-right: 0;
  padding-right: 8px;
}

/* 确保预览区域的背景色一致 */
.emoji-picker-container :deep(.emoji-mart-preview) {
  background-color: #2d3035;
}

/* 修复任何可能出现的滚动条问题 */
.emoji-picker-container :deep(::-webkit-scrollbar) {
  width: 6px;
  background-color: #2d3035;
}

.emoji-picker-container :deep(::-webkit-scrollbar-thumb) {
  background-color: #555;
  border-radius: 3px;
}

.emoji-picker-container :deep(::-webkit-scrollbar-track) {
  background-color: #2d3035;
}

/* 确保任何可能的溢出内容也应用背景色 */
.emoji-picker-container :deep(*) {
  scrollbar-color: #555 #2d3035;
  scrollbar-width: thin;
}

/* 修复任何内部元素的边缘 */
.emoji-picker-container :deep(.emoji-mart *) {
  box-sizing: border-box;
}

/* 分类标签 */
.emoji-picker-container :deep(.emoji-mart-category-label h3) {
  background-color: #2d3035;
  background-color: rgba(45, 48, 53, 0.95);
  color: #f0f0f0;
}

/* 导航栏 */
.emoji-picker-container :deep(.emoji-mart-bar) {
  padding: 0;
  border-color: #3a3a3a;
}

/* 底部预览区域 */
.emoji-picker-container :deep(.emoji-mart-preview) {
  border-top-color: #444;
}

/* 导航栏图标 */
.emoji-picker-container :deep(.emoji-mart-anchors) {
  padding: 0 4px;
  background-color: #252830;
  border-bottom: 1px solid #3a3a3a;
  color: #aaa;
}

.emoji-picker-container :deep(.emoji-mart-anchor:hover),
.emoji-picker-container :deep(.emoji-mart-anchor-selected) {
  color: var(--accent-color, #0076a8);
}

/* 搜索框 */
.emoji-picker-container :deep(.emoji-mart-search input) {
  background-color: #3c3f45;
  color: #f0f0f0;
  border-color: #555;
}

/* Emoji 悬停效果 */
.emoji-picker-container :deep(.emoji-mart-category .emoji-mart-emoji:hover:before),
.emoji-picker-container :deep(.emoji-mart-emoji-selected:before) {
  background-color: #4d5057;
}

/* 无结果文本 */
.emoji-picker-container :deep(.emoji-mart-no-results) {
  color: #aaa;
}

/* 预览区域 */
.emoji-picker-container :deep(.emoji-mart-preview-name) {
  color: #f0f0f0;
}

.emoji-picker-container :deep(.emoji-mart-preview-shortname) {
  color: #aaa;
}

/* 滚动条样式 */
.emoji-picker-container :deep(.emoji-mart-scroll::-webkit-scrollbar) {
  width: 6px;
  background: #2d3035;
}

.emoji-picker-container :deep(.emoji-mart-scroll::-webkit-scrollbar-thumb) {
  background: #555;
  border-radius: 3px;
}

/* 调整内部间距 */
.emoji-picker-container :deep(.emoji-mart-search) {
  margin: 6px 8px;
}

/* 确保内容区域填满空间 */
.emoji-picker-container :deep(.emoji-mart-category) {
  width: 100%;
}

/* 改进滚动条风格 */
.emoji-picker-container :deep(.emoji-mart-scroll) {
  padding: 0 8px 8px;
}

/* 确保描边和高亮颜色统一 */
.emoji-picker-container :deep(.emoji-mart-anchor-bar) {
  background-color: var(--accent-color, #0076a8);
}

.file-container {
  display: flex;
  align-items: center;
  padding: 8px;
  border-radius: 8px;
  background-color: rgba(255, 255, 255, 0.1);
  max-width: 300px;
}

.file-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-right: 12px;
  border-radius: 8px;
  background-color: rgba(255, 255, 255, 0.2);
  color: white;
}

.file-icon i {
  font-size: 20px;
}

.file-info {
  flex: 1;
  overflow: hidden;
}

.file-name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.file-size {
  font-size: 12px;
  opacity: 0.7;
}

.download-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-left: 8px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.2);
  color: white;
  transition: all 0.2s;
}

.download-btn:hover {
  background-color: var(--accent-color, #0076a8);
}

.message-bubble.file-message {
  padding: 0;
  overflow: hidden;
}
</style> 