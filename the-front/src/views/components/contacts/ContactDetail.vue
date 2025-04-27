<template>
  <div class="contact-detail" v-if="contact">
    <div class="contact-header">
      <el-avatar
        class="avatar"
        size="large"
        :class="{ 
          'user-avatar': type === 'private', 
          'group-avatar': type === 'group' 
        }"
      >
        {{ avatarText }}
      </el-avatar>
      <div class="contact-name">
        {{ type === 'private' ? contact.username : contact.groupName }}
      </div>
    </div>
    
    <div class="contact-body">
      <div v-if="type === 'private'" class="info-item">
        <div class="info-label">账号</div>
        <div class="info-value">{{ contact.userId }}</div>
      </div>
      <div v-else class="info-item">
        <div class="info-label">群号</div>
        <div class="info-value">{{ contact.groupId }}</div>
      </div>
      
      <div class="info-item" v-if="type === 'group'">
        <div class="info-label">成员数</div>
        <div class="info-value">{{ contact.memberCount || '--' }}</div>
      </div>
      
      <div class="contact-actions">
        <el-button type="primary" @click="startChat">
          {{ type === 'private' ? '发消息' : '进入群聊' }}
        </el-button>
        <el-button type="danger">
          {{ type === 'private' ? '删除好友' : '退出群组' }}
        </el-button>
      </div>
    </div>
  </div>
  
  <div class="contact-detail empty" v-else>
    <el-empty description="请选择一个联系人查看详情" />
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { User, ChatLineRound } from '@element-plus/icons-vue';
import { useRouter } from 'vue-router';

const router = useRouter();

const props = defineProps({
  contact: {
    type: Object,
    default: null
  },
  type: {
    type: String,
    default: '',
    validator: (value) => ['private', 'group', ''].includes(value)
  }
});

const avatarStyle = computed(() => {
  return props.type === 'group' ? { backgroundColor: '#409EFF' } : {};
});

const getInitial = () => {
  if (!props.contact) return '';
  
  if (props.type === 'private') {
    return props.contact.username?.charAt(0).toUpperCase() || '';
  } else {
    return props.contact.groupName?.charAt(0).toUpperCase() || '';
  }
};

const getName = () => {
  if (!props.contact) return '';
  
  if (props.type === 'private') {
    return props.contact.username || '';
  } else {
    return props.contact.groupName || '';
  }
};

const startChat = () => {
  // 跳转到聊天页面，并传递联系人信息
  router.push({
    name: 'chat',
    params: {
      type: props.type,
      id: props.type === 'private' ? props.contact.userId : props.contact.groupId
    }
  });
};
</script>

<style scoped>
.contact-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--primary-color);
}

.contact-header {
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  background: var(--gradient-primary);
}

.avatar {
  font-size: 20px;
  font-weight: bold;
  box-shadow: var(--shadow-sm);
}

.user-avatar {
  background-color: var(--accent-color) !important;
}

.group-avatar {
  background-color: var(--accent-color) !important;
}

.contact-name {
  margin-left: 20px;
  font-size: 20px;
  font-weight: bold;
  color: var(--text-color);
}

.contact-body {
  padding: 24px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  background-color: var(--primary-color);
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: var(--secondary-color);
  border-radius: var(--border-radius-md);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-fast);
}

.info-item:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.info-label {
  font-weight: bold;
  color: var(--text-secondary);
}

.info-value {
  margin-left: 20px;
  color: var(--text-color);
}

.contact-actions {
  display: flex;
  gap: 12px;
  margin-top: 30px;
  justify-content: center;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-secondary);
}

.empty {
  background-color: var(--primary-color);
  display: flex;
  justify-content: center;
  align-items: center;
  background-image: 
    radial-gradient(circle at 20% 30%, rgba(0, 184, 255, 0.05) 0%, transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(0, 184, 255, 0.05) 0%, transparent 40%);
}
</style> 