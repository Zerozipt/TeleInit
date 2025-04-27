<template>
  <div class="add-friend-content">
    <el-input v-model="friendSearchTerm" placeholder="输入用户名搜索" @keyup.enter.native="handleSearchUsers"/>
    <div class="search-results" v-if="searchResults.length > 0">
      <div v-for="user in searchResults" :key="user.userId" class="search-result-item">
        <el-avatar size="small">{{ user.username.substring(0, 1) }}</el-avatar>
        <span>{{ user.username }}</span>
        <el-button type="primary" size="small" @click="handleAddFriend(user.id,user.username)" :loading="addingFriendId === user.userId">添加</el-button>
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
import { ref } from 'vue';
import { searchUsers as apiSearchUsers, addFriend as apiAddFriend } from '@/api/friendApi';
import { ElMessage } from 'element-plus';

const props = defineProps({
  currentUserId: [String, Number]
});

const emit = defineEmits(['friend-request-sent']);

const friendSearchTerm = ref('');
const searchResults = ref([]);
const hasSearched = ref(false);
const isSearching = ref(false);
const addingFriendId = ref(null);

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
    searchResults.value = searchResults.value.filter(user => user.userId !== userId);
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
</style> 