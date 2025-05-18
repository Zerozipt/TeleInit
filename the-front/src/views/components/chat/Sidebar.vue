<template>
  <div class="sidebar">
    <!-- 用户信息 -->
    <div class="user-profile">
      <el-avatar :size="40" :src="currentUserAvatar">{{ currentUser?.substring(0, 1) }}</el-avatar>
      <span class="username">{{ currentUser }}</span>
    </div>

    <!-- 搜索框 -->
    <div class="search-box">
      <el-input
        v-model="localSearchTerm"
        placeholder="搜索"
        clearable
        >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>

    <!-- 联系人列表 -->
    <div class="contact-list">
      <div
        v-for="item in contacts"
        :key="itemKey(item)"
        class="contact-item"
        :class="{ 
          active: isContactActive(item),
          'has-unread': hasUnread(item)
        }"
        @click="selectContact(item)"
      >
        <div class="avatar-container">
          <el-avatar :size="40" :style="avatarStyle(item)">
            {{ getInitial(item) }}
          </el-avatar>
          <div v-if="getUnreadCount(item) > 0" class="unread-badge">
            {{ getUnreadCount(item) > 99 ? '99+' : getUnreadCount(item) }}
          </div>
        </div>
        <div class="contact-info">
          <div class="contact-name">{{ getName(item) }}</div>
          <div v-if="getLatestMessage(item)" class="message-preview">
            {{ getLatestMessage(item) }}
          </div>
        </div>
      </div>
      <div v-if="contacts.length === 0" class="empty-tip">
        没有联系人，请添加好友或加入群组
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Search } from '@element-plus/icons-vue';

const props = defineProps({
  currentUser: String,
  currentUserAvatar: String,
  userId: String,
  friends: Array,
  groups: Array,
  selectedContact: Object,
  chatType: String,
  unreadMessages: {
    type: Map,
    default: () => new Map()
  },
  latestMessages: {
    type: Map,
    default: () => new Map()
  }
});

const emit = defineEmits(['select-contact']);

const localSearchTerm = ref('');

// 判断好友关系中当前用户是first还是second
const isCurrentUserFirst = (friend) => {
  const result = String(friend.firstUserId) === String(props.userId);
  return result;
};

// 获取好友对象中对方的用户ID
const getFriendUserId = (friend) => {
  return isCurrentUserFirst(friend) ? friend.secondUserId : friend.firstUserId;
};

// 获取好友对象中对方的用户名
const getFriendUsername = (friend) => {
  return isCurrentUserFirst(friend) ? friend.secondUsername : friend.firstUsername;
};

// 合并、过滤并排序联系人列表
const contacts = computed(() => {
  // 合并并标记类型
  let list = [
    ...props.friends.map(f => ({ ...f, __type: 'private' })),
    ...props.groups.map(g => ({ ...g, __type: 'group' }))
  ];
  // 过滤
  const term = localSearchTerm.value.trim().toLowerCase();
  if (term) {
    list = list.filter(item => {
      const name = item.__type === 'private' 
        ? getFriendUsername(item)
        : item.groupName;
      return name?.toLowerCase().includes(term);
    });
  }
  // 置顶已选联系人
  if (props.selectedContact) {
    list.sort((a, b) => {
      if (props.chatType === 'private') {
        // 如果是私聊，比较对方的userId
        const aId = a.__type === 'private' ? getFriendUserId(a) : a.groupId;
        const bId = b.__type === 'private' ? getFriendUserId(b) : b.groupId;
        const selId = props.selectedContact.userId;
        
        if (a.__type === 'private' && aId === selId) return -1;
        if (b.__type === 'private' && bId === selId) return 1;
      } else {
        // 如果是群聊，比较groupId
        const aId = a.__type === 'private' ? getFriendUserId(a) : a.groupId;
        const bId = b.__type === 'private' ? getFriendUserId(b) : b.groupId;
        const selId = props.selectedContact.groupId;
        
        if (a.__type === 'group' && aId === selId) return -1;
        if (b.__type === 'group' && bId === selId) return 1;
      }
      return 0;
    });
  }
  return list;
});

// 检查联系人是否处于激活状态
const isContactActive = (item) => {
  if (!props.selectedContact) return false;
  
  if (item.__type === 'private') {
    const friendId = getFriendUserId(item);
    return props.chatType === 'private' && props.selectedContact?.userId === friendId;
  } else if (item.__type === 'group') {
    return props.chatType === 'group' && props.selectedContact?.groupId === item.groupId;
  }
  return false;
};

// 点击联系人，传递处理后的对象及类型
const selectContact = (item) => {
  const type = item.__type;
  
  if (type === 'private') {
    // 对于好友，需要创建一个适合聊天界面的对象，包含对方的ID和用户名
    const processedContact = {
      userId: getFriendUserId(item),
      username: getFriendUsername(item),
      // 保留原始数据以便其他地方可能需要
      originalFriend: item
    };
    emit('select-contact', processedContact, type);
  } else {
    // 群组不需要特殊处理
    const contact = props.groups.find(g => g.groupId === item.groupId);
    emit('select-contact', contact || item, type);
  }
};

// 渲染列表唯一 key
const itemKey = (item) => {
  if (item.__type === 'private') {
    return `${item.__type}-${getFriendUserId(item)}`;
  } else {
    return `${item.__type}-${item.groupId}`;
  }
};

const avatarStyle = (item) => {
  if (item.__type === 'private') return {};
  if (item.__type === 'group') return { backgroundColor: '#409EFF' };
  return {};
};

const getInitial = (item) => {
  if (item.__type === 'private') {
    const username = getFriendUsername(item);
    return username?.substring(0, 1) || '';
  }
  if (item.__type === 'group') return item.groupName?.substring(0, 1) || '';
  return '';
};

const getName = (item) => {
  if (item.__type === 'private') return getFriendUsername(item) || '';
  if (item.__type === 'group') return item.groupName || '';
  return '';
};

// 获取未读消息数
const getUnreadCount = (item) => {
  const key = item.__type === 'private'
    ? getFriendUserId(item) 
    : item.groupId;
  return props.unreadMessages.get(key) || 0;
};

// 判断是否有未读消息
const hasUnread = (item) => {
  return getUnreadCount(item) > 0;
};

// 获取最近一条消息
const getLatestMessage = (item) => {
  const key = item.__type === 'private'
    ? getFriendUserId(item) 
    : item.groupId;
  return props.latestMessages.get(key) || '';
};

// Watch for prop changes if needed, e.g., to reset search or tab
// watch(() => props.friends, () => { /* Handle updates */ });
// watch(() => props.groups, () => { /* Handle updates */ });

</script>

<style scoped>
.sidebar {
  width: 300px;
  background-color: var(--secondary-color);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: all var(--transition-normal);
}

.user-profile {
  padding: 16px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  background: var(--gradient-primary);
}

.username {
  margin-left: 10px;
  font-weight: bold;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-color);
}

.search-box {
  padding: 8px 16px;
  margin: 0;
  border-bottom: none;
  flex-shrink: 0;
  background-color: var(--primary-color);
  display: flex;
  width: 100%;
  transition: background-color 0.2s ease;
}

/* 添加搜索框悬停效果 */
.search-box:hover {
  background-color: rgba(255, 255, 255, 0.05);
}

.search-box .el-input {
  width: 100%;
}

.search-box :deep(.el-input__wrapper) {
  background-color: transparent !important;
  box-shadow: none !important;
  width: 100% !important;
  margin: 0 !important;
  padding: 0 !important;
  border-radius: 0 !important;
  border: none !important;
  position: relative !important;
}

/* 添加聚焦状态样式覆盖 */
.search-box :deep(.el-input__wrapper.is-focus) {
  box-shadow: none !important;
  border: none !important;
  outline: none !important;
}

/* 添加悬浮状态样式覆盖 */
.search-box :deep(.el-input__wrapper:hover) {
  box-shadow: none !important;
  border: none !important;
}

/* 修改输入框内部文字样式 */
.search-box :deep(.el-input__inner) {
  height: 32px !important;
  line-height: 32px !important;
  padding: 0 8px 0 35px !important;
  color: var(--text-secondary) !important;
  font-size: 14px !important;
  background-color: transparent !important;
}

/* 调整前缀图标插槽样式 */
.search-box :deep(.el-input__prefix) {
  color: var(--text-secondary) !important;
  display: flex;
  align-items: center;
  padding-left: 10px;
  height: 100%;
  position: absolute;
  left: 0;
}

/* 确保没有任何子元素有额外的外边距 */
.search-box :deep(*) {
  margin: 0;
}

.contact-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  background-color: var(--primary-color);
}

/* 滚动条样式 */
.contact-list::-webkit-scrollbar {
  width: 6px;
}

.contact-list::-webkit-scrollbar-track {
  background: var(--secondary-color);
  border-radius: 3px;
}

.contact-list::-webkit-scrollbar-thumb {
  background: var(--accent-color);
  border-radius: 3px;
}

.contact-list::-webkit-scrollbar-thumb:hover {
  background: #0099cc;
}

.contact-item {
  display: flex;
  align-items: center;
  padding: 10px;
  margin: 8px 0;
  border-radius: var(--border-radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  background-color: var(--secondary-color);
  box-shadow: var(--shadow-sm);
}

.contact-item:hover {
  background-color: var(--hover-color);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.contact-item.active {
  background: rgba(64, 158, 255, 0.6);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
}

.contact-item.active .contact-name {
  color: rgba(255, 255, 255, 0.9);
}

.contact-info {
  margin-left: 10px;
  overflow: hidden;
}

.contact-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-color);
}

.empty-tip {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
}

/* 添加悬浮动画 */
/* @keyframes float { */
/*   0% { */
/*     transform: translateY(0px); */
/*   } */
/*   50% { */
/*     transform: translateY(-5px); */
/*   } */
/*   100% { */
/*     transform: translateY(0px); */
/*   } */
/* } */

/* .contact-item.active { */
/*   animation: float 3s ease-in-out infinite; */
/* } */

.avatar-container {
  position: relative;
}

.unread-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background-color: #f56c6c;
  color: white;
  border-radius: 10px;
  padding: 0 6px;
  height: 20px;
  min-width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.message-preview {
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
  margin-top: 4px;
}

.contact-item.has-unread {
  background-color: rgba(64, 158, 255, 0.1);
}

.contact-item.has-unread .message-preview {
  color: var(--text-color);
  font-weight: 500;
}
</style> 