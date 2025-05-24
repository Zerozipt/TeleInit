<template>
  <div class="add-friend-content">
    <!-- 搜索输入框 -->
    <div class="search-container">
      <el-input 
        v-model="friendSearchTerm" 
        placeholder="输入用户名搜索" 
        @keyup.enter.native="handleSearchUsers"
        size="large"
        clearable
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button 
            type="primary" 
            @click="handleSearchUsers" 
            :loading="isSearching"
            :icon="Search"
          >
            搜索
          </el-button>
        </template>
      </el-input>
    </div>

    <!-- 搜索结果 -->
    <div class="search-results" v-if="searchResults.length > 0">
      <div class="results-header">
        <span class="results-count">找到 {{ searchResults.length }} 个用户</span>
      </div>
      <div v-for="user in searchResults" :key="user.id" class="search-result-item">
        <div class="user-avatar-container">
          <el-avatar 
            :size="48" 
            class="user-avatar"
            :style="{ backgroundColor: getAvatarColor(user.username) }"
          >
            {{ user.username.substring(0, 1).toUpperCase() }}
          </el-avatar>
          <div class="online-indicator"></div>
        </div>
        
        <div class="user-info">
          <div class="username">{{ user.username }}</div>
          <div class="user-id">ID: {{ user.id }}</div>
        </div>
        
        <div class="action-area">
          <el-button 
            v-if="!getUserRequestStatus(user.id)"
            type="primary" 
            @click="handleAddFriend(user.id, user.username)" 
            :loading="addingFriendId === user.id"
            round
          >
            <el-icon><Plus /></el-icon>
            添加好友
          </el-button>
          <el-button 
            v-else-if="getUserRequestStatus(user.id) === 'sending'"
            type="primary" 
            loading
            disabled
            round
          >
            发送中...
          </el-button>
          <el-button 
            v-else-if="getUserRequestStatus(user.id) === 'sent'"
            type="success" 
            disabled
            round
          >
            <el-icon><Check /></el-icon>
            已发送
          </el-button>
          <el-button 
            v-else-if="getUserRequestStatus(user.id) === 'failed'"
            type="danger" 
            @click="handleAddFriend(user.id, user.username)"
            round
          >
            <el-icon><RefreshRight /></el-icon>
            重试
          </el-button>
        </div>
      </div>
    </div>
    
    <!-- 无结果状态 -->
    <div v-else-if="friendSearchTerm && hasSearched" class="no-results">
      <el-empty description="没有找到相关用户">
        <template #image>
          <el-icon size="64" color="#d9d9d9"><Search /></el-icon>
        </template>
      </el-empty>
    </div>
    
    <!-- 默认提示状态 -->
    <div v-else class="search-prompt">
      <div class="prompt-content">
        <el-icon size="48" color="#c0c4cc"><UserFilled /></el-icon>
        <h3>搜索用户</h3>
        <p>输入用户名来搜索并添加好友</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { searchUsers as apiSearchUsers, addFriend as apiAddFriend } from '@/api/friendApi';
import { ElMessage } from 'element-plus';
import stompClientInstance from '@/net/websocket';
import { Search, Plus, Check, RefreshRight, UserFilled } from '@element-plus/icons-vue';

const props = defineProps({
  currentUserId: [String, Number]
});

const emit = defineEmits(['friend-request-sent']);

const friendSearchTerm = ref('');
const searchResults = ref([]);
const hasSearched = ref(false);
const isSearching = ref(false);
const addingFriendId = ref(null);

// 乐观更新：跟踪每个用户的请求状态
// 状态：null(未操作) | 'sending'(发送中) | 'sent'(已发送) | 'failed'(失败)
const userRequestStatus = reactive({});

// 获取用户请求状态
const getUserRequestStatus = (userId) => {
  return userRequestStatus[userId] || null;
};

const handleSearchUsers = async () => {
  if (!friendSearchTerm.value.trim()) {
      ElMessage.warning('请输入要搜索的用户名');
      return;
  }
  isSearching.value = true;
  hasSearched.value = false;
  searchResults.value = [];

  try {
    const results = await apiSearchUsers(friendSearchTerm.value.trim());
    searchResults.value = results.filter(user => user.id !== props.currentUserId);
    hasSearched.value = true;
    
    // 重置所有用户的请求状态
    Object.keys(userRequestStatus).forEach(key => {
      delete userRequestStatus[key];
    });
  } catch (error) {
    console.error('搜索用户失败:', error);
    ElMessage.error(`搜索用户失败: ${error.message || '请稍后重试'}`);
  } finally {
      isSearching.value = false;
  }
};

const handleAddFriend = async (userId, username) => {
  // 乐观更新：立即设置为发送中状态
  userRequestStatus[userId] = 'sending';
  addingFriendId.value = userId;
  
  try {
    // 调用API发送好友请求
    await apiAddFriend(userId, username);
    
    // 成功：更新为已发送状态
    userRequestStatus[userId] = 'sent';
    ElMessage.success('好友请求已发送');
    emit('friend-request-sent', userId);
    
    // 乐观更新WebSocket实例的好友请求列表
    const newFriendRequest = {
      firstUserId: props.currentUserId.toString(),
      secondUserId: userId.toString(),
      firstUsername: stompClientInstance.currentUser.value,
      secondUsername: username,
      status: 'requested',
      displayStatus: 'sent',
      created_at: new Date().toISOString(),
      tempId: Date.now() // 临时ID，用于后续确认
    };
    
    // 添加到本地好友请求列表（乐观更新）
    if (stompClientInstance.friendRequests.value) {
      stompClientInstance.friendRequests.value.unshift(newFriendRequest);
    }
    
    // 设置超时重试机制（10秒后如果没收到WebSocket确认则标记为可能失败）
    setTimeout(() => {
      if (userRequestStatus[userId] === 'sent') {
        // 检查是否已经收到WebSocket确认
        const confirmed = stompClientInstance.friendRequests.value?.some(req => 
          req.firstUserId === props.currentUserId.toString() && 
          req.secondUserId === userId.toString() &&
          !req.tempId // 后端返回的真实数据没有tempId
        );
        
        if (!confirmed) {
          console.warn('好友请求可能未成功发送到服务器');
          // 可以选择保持已发送状态或标记为需要重试
        }
      }
    }, 10000);
    
  } catch (error) {
    console.error('添加好友失败:', error);
    
    // 失败：更新为失败状态，允许重试
    userRequestStatus[userId] = 'failed';
    ElMessage.error(`添加好友失败: ${error.message || '请检查用户是否存在或网络问题'}`);
    
    // 移除乐观添加的请求记录
    if (stompClientInstance.friendRequests.value) {
      stompClientInstance.friendRequests.value = stompClientInstance.friendRequests.value.filter(req => 
        !(req.firstUserId === props.currentUserId.toString() && 
          req.secondUserId === userId.toString() && 
          req.tempId)
      );
    }
  } finally {
      addingFriendId.value = null;
  }
};

// 生成头像颜色的函数
const getAvatarColor = (username) => {
  const colors = [
    '#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4', '#ffeaa7',
    '#dda0dd', '#98d8c8', '#f7dc6f', '#bb8fce', '#85c1e9',
    '#f8c471', '#82e0aa', '#f1948a', '#85929e', '#a9cce3'
  ];
  const index = username.charCodeAt(0) % colors.length;
  return colors[index];
};
</script>

<style scoped>
.add-friend-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--primary-color);
  padding: 20px;
  box-sizing: border-box;
}

.search-container {
  margin-bottom: 20px;
}

.search-results {
  flex: 1;
  background: var(--secondary-color);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
}

.results-header {
  padding: 15px 20px;
  background: var(--background-light);
  border-bottom: 1px solid var(--border-color);
}

.results-count {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.search-result-item {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
  transition: all var(--transition-fast);
  background: var(--secondary-color);
}

.search-result-item:hover {
  background: var(--hover-color);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.search-result-item:last-child {
  border-bottom: none;
}

.user-avatar-container {
  position: relative;
  margin-right: 16px;
}

.user-avatar {
  border: 2px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  font-weight: 600;
  font-size: 18px;
  color: white !important;
}

.online-indicator {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 12px;
  height: 12px;
  background: var(--success-color);
  border: 2px solid var(--secondary-color);
  border-radius: 50%;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.username {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 4px;
  line-height: 1.2;
}

.user-id {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: 'Monaco', 'Courier New', monospace;
}

.action-area {
  margin-left: 16px;
}

.no-results,
.search-prompt {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--secondary-color);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-md);
  border: 1px solid var(--border-color);
}

.prompt-content {
  text-align: center;
  padding: 40px;
}

.prompt-content h3 {
  margin: 16px 0 8px;
  color: var(--text-color);
  font-size: 20px;
  font-weight: 600;
}

.prompt-content p {
  color: var(--text-secondary);
  font-size: 14px;
  margin: 0;
  line-height: 1.5;
}

/* 按钮样式增强 */
.el-button {
  border-radius: 20px;
  font-weight: 500;
  padding: 8px 20px;
  transition: all var(--transition-fast);
}

.el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.el-button.is-disabled {
  transform: none;
  box-shadow: none;
}

.el-button--success.is-disabled {
  background-color: var(--success-color);
  border-color: var(--success-color);
  color: white;
  opacity: 0.8;
}

.el-button--danger:not(.is-disabled):hover {
  background-color: var(--error-color);
  border-color: var(--error-color);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .add-friend-content {
    padding: 16px;
  }
  
  .search-result-item {
    padding: 12px 16px;
  }
  
  .user-avatar-container {
    margin-right: 12px;
  }
  
  .user-avatar {
    width: 40px !important;
    height: 40px !important;
    font-size: 16px;
  }
  
  .username {
    font-size: 15px;
  }
  
  .action-area .el-button {
    padding: 6px 16px;
    font-size: 13px;
  }
}

/* 滚动条美化 */
.search-results::-webkit-scrollbar {
  width: 6px;
}

.search-results::-webkit-scrollbar-track {
  background: var(--secondary-color);
  border-radius: 3px;
}

.search-results::-webkit-scrollbar-thumb {
  background: var(--accent-color);
  border-radius: 3px;
}

.search-results::-webkit-scrollbar-thumb:hover {
  background: var(--accent-dark);
}

/* 覆盖Element Plus样式以适配暗色主题 */
:deep(.el-empty__description) {
  color: var(--text-secondary) !important;
}

:deep(.el-icon) {
  color: var(--text-secondary) !important;
}

/* 搜索框暗色主题适配 */
:deep(.el-input__wrapper) {
  background-color: var(--tertiary-color) !important;
  box-shadow: 0 0 0 1px var(--border-color) inset !important;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--accent-color) inset !important;
}

:deep(.el-input__inner) {
  background-color: transparent !important;
  color: var(--text-color) !important;
}

:deep(.el-input__prefix) {
  color: var(--text-secondary) !important;
}

:deep(.el-input__suffix) {
  color: var(--text-secondary) !important;
}

/* 搜索按钮（append区域）暗色主题适配 */
:deep(.el-input-group__append) {
  background-color: var(--tertiary-color) !important;
  border-color: var(--border-color) !important;
  color: var(--text-color) !important;
  border-left: 1px solid var(--border-color) !important;
}

:deep(.el-input-group__append .el-button--primary) {
  background-color: var(--accent-color) !important;
  border-color: var(--accent-color) !important;
  color: white !important;
  margin: 0 !important;
  border: 1px solid var(--accent-color) !important;
}

:deep(.el-input-group__append .el-button--primary:hover) {
  background-color: var(--accent-dark) !important;
  border-color: var(--accent-dark) !important;
  border: 1px solid var(--accent-dark) !important;
}

/* 增强搜索图标显示效果 */
:deep(.el-input__prefix .el-icon) {
  color: var(--accent-color) !important;
  font-size: 16px !important;
  font-weight: 600 !important;
}

:deep(.el-input-group__append .el-button .el-icon) {
  color: white !important;
  font-size: 16px !important;
  font-weight: 600 !important;
}

/* 增强添加好友按钮中的图标显示效果 */
.action-area .el-button .el-icon {
  font-size: 16px !important;
  font-weight: 600 !important;
  margin-right: 4px !important;
}

:deep(.action-area .el-button--primary .el-icon) {
  color: white !important;
  font-weight: 700 !important;
}

:deep(.action-area .el-button--success .el-icon) {
  color: white !important;
  font-weight: 700 !important;
}

:deep(.action-area .el-button--danger .el-icon) {
  color: white !important;
  font-weight: 700 !important;
}

/* 按钮暗色主题适配 */
:deep(.el-button--primary) {
  background-color: var(--accent-color) !important;
  border-color: var(--accent-color) !important;
  color: white !important;
}

:deep(.el-button--primary:hover) {
  background-color: var(--accent-dark) !important;
  border-color: var(--accent-dark) !important;
}

:deep(.el-button--success) {
  background-color: var(--success-color) !important;
  border-color: var(--success-color) !important;
  color: white !important;
}

:deep(.el-button--danger) {
  background-color: var(--error-color) !important;
  border-color: var(--error-color) !important;
  color: white !important;
}
</style> 