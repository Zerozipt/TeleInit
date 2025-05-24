<template>
  <el-dialog
    :model-value="visible"
    title="添加好友"
    width="500px"
    @update:modelValue="closeDialog"
    @closed="resetState"
    :close-on-click-modal="false"
  >
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
      </el-input>
    </div>

    <!-- 搜索结果 -->
    <div class="search-results" v-if="searchResults.length > 0">
      <div class="results-header">
        <span class="results-count">找到 {{ searchResults.length }} 个用户</span>
      </div>
      <div class="results-list">
        <div v-for="user in searchResults" :key="user.id" class="search-result-item">
          <div class="user-avatar-container">
            <el-avatar 
              :size="40" 
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
              type="primary" 
              size="small" 
              @click="handleAddFriend(user.id, user.username)" 
              :loading="addingFriendId === user.id"
              round
            >
              <el-icon><Plus /></el-icon>
              添加
            </el-button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 无结果状态 -->
    <div v-else-if="friendSearchTerm && hasSearched" class="no-results">
      <el-empty description="没有找到相关用户" :image-size="80">
        <template #image>
          <el-icon size="48" color="#d9d9d9"><Search /></el-icon>
        </template>
      </el-empty>
    </div>
    
    <!-- 默认提示状态 -->
    <div v-else class="search-prompt">
      <div class="prompt-content">
        <el-icon size="40" color="#c0c4cc"><UserFilled /></el-icon>
        <h4>搜索用户</h4>
        <p>输入用户名来搜索并添加好友</p>
      </div>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="closeDialog">取消</el-button>
        <el-button type="primary" @click="handleSearchUsers" :loading="isSearching" :icon="Search">
          搜索
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { searchUsers as apiSearchUsers, addFriend as apiAddFriend } from '@/api/friendApi';
import { ElMessage } from 'element-plus';
import { Search, Plus, UserFilled } from '@element-plus/icons-vue';

const props = defineProps({
  visible: Boolean,
  currentUserId: [String, Number]
});

const emit = defineEmits(['update:visible', 'friend-request-sent']);

const friendSearchTerm = ref('');
const searchResults = ref([]);
const hasSearched = ref(false);
const isSearching = ref(false);
const addingFriendId = ref(null);

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

const closeDialog = () => {
  emit('update:visible', false);
};

const resetState = () => {
    friendSearchTerm.value = '';
    searchResults.value = [];
    hasSearched.value = false;
    isSearching.value = false;
    addingFriendId.value = null;
};

watch(() => props.visible, (newVal) => {
  if (newVal) {
    // 对话框打开时可以做一些初始化操作
  } else {
    // 对话框关闭时清理状态
  }
});

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
  } catch (error) {
    console.error('搜索用户失败:', error);
    ElMessage.error(`搜索用户失败: ${error.message || '请稍后重试'}`);
  } finally {
      isSearching.value = false;
  }
};

const handleAddFriend = async (userId, username) => {
  addingFriendId.value = userId;
  try {
    await apiAddFriend(userId, username);
    ElMessage.success('好友请求已发送');
    emit('friend-request-sent', userId);
    searchResults.value = searchResults.value.filter(user => user.id !== userId);
    if (searchResults.value.length === 0) {
        hasSearched.value = false;
    }
  } catch (error) {
    console.error('添加好友失败:', error);
    ElMessage.error(`添加好友失败: ${error.message || '请检查用户是否存在或网络问题'}`);
  } finally {
      addingFriendId.value = null;
  }
};
</script>

<style scoped>
.search-container {
  margin-bottom: 20px;
}

.search-results {
  background: var(--background-light);
  border-radius: var(--border-radius-md);
  overflow: hidden;
  max-height: 400px;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-color);
}

.results-header {
  padding: 12px 16px;
  background: var(--background-lighter);
  border-bottom: 1px solid var(--border-color);
}

.results-count {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.results-list {
  max-height: 320px;
  overflow-y: auto;
}

.search-result-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
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
  margin-right: 12px;
}

.user-avatar {
  border: 2px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  font-weight: 600;
  font-size: 16px;
  color: white !important;
}

.online-indicator {
  position: absolute;
  bottom: 1px;
  right: 1px;
  width: 10px;
  height: 10px;
  background: var(--success-color);
  border: 2px solid var(--secondary-color);
  border-radius: 50%;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.username {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 2px;
  line-height: 1.2;
}

.user-id {
  font-size: 11px;
  color: var(--text-secondary);
  font-family: 'Monaco', 'Courier New', monospace;
}

.action-area {
  margin-left: 12px;
}

.no-results,
.search-prompt {
  padding: 40px 20px;
  text-align: center;
  background: var(--background-light);
  border-radius: var(--border-radius-md);
  margin: 10px 0;
  border: 1px solid var(--border-color);
}

.prompt-content h4 {
  margin: 12px 0 6px;
  color: var(--text-color);
  font-size: 16px;
  font-weight: 600;
}

.prompt-content p {
  color: var(--text-secondary);
  font-size: 13px;
  margin: 0;
  line-height: 1.4;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 按钮样式增强 */
.el-button {
  border-radius: 16px;
  font-weight: 500;
  transition: all var(--transition-fast);
}

.el-button:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.el-button.is-loading {
  transform: none;
  box-shadow: none;
}

/* 滚动条美化 */
.results-list::-webkit-scrollbar {
  width: 4px;
}

.results-list::-webkit-scrollbar-track {
  background: var(--secondary-color);
  border-radius: 2px;
}

.results-list::-webkit-scrollbar-thumb {
  background: var(--accent-color);
  border-radius: 2px;
}

.results-list::-webkit-scrollbar-thumb:hover {
  background: var(--accent-dark);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .search-result-item {
    padding: 10px 12px;
  }
  
  .user-avatar-container {
    margin-right: 10px;
  }
  
  .user-avatar {
    width: 36px !important;
    height: 36px !important;
    font-size: 14px;
  }
  
  .username {
    font-size: 13px;
  }
  
  .action-area .el-button {
    padding: 4px 12px;
    font-size: 12px;
  }
}

/* 覆盖Element Plus对话框样式以适配暗色主题 */
:deep(.el-dialog) {
  background-color: var(--secondary-color) !important;
  border: 1px solid var(--border-color) !important;
}

:deep(.el-dialog__header) {
  background-color: var(--background-light) !important;
  border-bottom: 1px solid var(--border-color) !important;
  padding: 16px 20px !important;
  margin: 0 !important;
}

:deep(.el-dialog__title) {
  color: var(--text-color) !important;
  font-weight: 600 !important;
}

:deep(.el-dialog__headerbtn .el-dialog__close) {
  color: var(--text-secondary) !important;
}

:deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  color: var(--accent-color) !important;
}

:deep(.el-dialog__body) {
  background-color: var(--secondary-color) !important;
  color: var(--text-color) !important;
  padding: 20px !important;
}

:deep(.el-dialog__footer) {
  background-color: var(--background-light) !important;
  border-top: 1px solid var(--border-color) !important;
  padding: 16px 20px !important;
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

/* 增强搜索图标显示效果 */
:deep(.el-input__prefix .el-icon) {
  color: var(--accent-color) !important;
  font-size: 16px !important;
  font-weight: 600 !important;
}

/* 增强添加好友按钮中的图标显示效果 */
.action-area .el-button .el-icon {
  font-size: 14px !important;
  font-weight: 600 !important;
  margin-right: 3px !important;
}

:deep(.action-area .el-button--primary .el-icon) {
  color: white !important;
  font-weight: 700 !important;
}

/* 底部按钮区域图标增强 */
:deep(.dialog-footer .el-button .el-icon) {
  font-size: 16px !important;
  font-weight: 600 !important;
  margin-right: 4px !important;
}

:deep(.dialog-footer .el-button--primary .el-icon) {
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

:deep(.el-button--default) {
  background-color: var(--tertiary-color) !important;
  border-color: var(--border-color) !important;
  color: var(--text-color) !important;
}

:deep(.el-button--default:hover) {
  background-color: var(--hover-color) !important;
  border-color: var(--accent-color) !important;
  color: var(--accent-color) !important;
}
</style> 