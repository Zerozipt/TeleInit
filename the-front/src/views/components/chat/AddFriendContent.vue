<template>
  <div class="add-friend-content">
    <el-input v-model="friendSearchTerm" placeholder="输入用户名搜索" @keyup.enter.native="handleSearchUsers"/>
    <div class="search-results" v-if="searchResults.length > 0">
      <div v-for="user in searchResults" :key="user.userId" class="search-result-item">
        <el-avatar size="small">{{ user.username.substring(0, 1) }}</el-avatar>
        <span>{{ user.username }}</span>
        <el-button 
          v-if="!getUserRequestStatus(user.userId)"
          type="primary" 
          size="small" 
          @click="handleAddFriend(user.id, user.username)" 
          :loading="addingFriendId === user.userId"
        >
          添加
        </el-button>
        <el-button 
          v-else-if="getUserRequestStatus(user.userId) === 'sending'"
          type="primary" 
          size="small" 
          loading
          disabled
        >
          发送中...
        </el-button>
        <el-button 
          v-else-if="getUserRequestStatus(user.userId) === 'sent'"
          type="success" 
          size="small" 
          disabled
          icon="Check"
        >
          已发送
        </el-button>
        <el-button 
          v-else-if="getUserRequestStatus(user.userId) === 'failed'"
          type="danger" 
          size="small" 
          @click="handleAddFriend(user.id, user.username)"
          icon="RefreshRight"
        >
          重试
        </el-button>
      </div>
    </div>
    <div v-else-if="friendSearchTerm && hasSearched" class="no-results">
      无搜索结果
    </div>
    <div v-else class="search-prompt">
      输入用户名进行搜索
    </div>
    <div class="action-buttons">
      <el-button type="primary" @click="handleSearchUsers" :loading="isSearching">搜索</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { searchUsers as apiSearchUsers, addFriend as apiAddFriend } from '@/api/friendApi';
import { ElMessage } from 'element-plus';
import stompClientInstance from '@/net/websocket';

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
    searchResults.value = results.filter(user => user.userId !== props.currentUserId);
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
</script>

<style scoped>
.add-friend-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.search-results {
  margin-top: 15px;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #eee;
  border-radius: 4px;
  flex: 1;
}

.search-result-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #eee;
  transition: background-color 0.2s;
}

.search-result-item:hover {
  background-color: #f5f7fa;
}

.search-result-item:last-child {
    border-bottom: none;
}

.search-result-item span {
  flex: 1;
  margin: 0 10px;
}

.no-results,
.search-prompt {
  padding: 20px;
  text-align: center;
  color: #999;
  margin-top: 15px;
  flex: 1;
}

.action-buttons {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.el-input {
    margin-bottom: 10px;
}

/* 状态按钮样式增强 */
.el-button.is-disabled {
  cursor: not-allowed;
}

.el-button--success.is-disabled {
  background-color: #67c23a;
  border-color: #67c23a;
  color: white;
}

.el-button--danger:not(.is-disabled):hover {
  background-color: #f56c6c;
  border-color: #f56c6c;
}
</style> 