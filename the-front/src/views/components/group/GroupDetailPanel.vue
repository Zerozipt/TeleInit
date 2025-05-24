<template>
  <div class="group-detail-panel">
    <el-card v-if="loading" class="loading-card">
      <el-skeleton animated :rows="4" />
    </el-card>

    <template v-else-if="groupDetail">
      <!-- 群组基本信息 -->
      <div class="group-header">
        <div class="group-avatar">
          <el-avatar :size="50" :src="null" style="background-color: #409EFF">
            {{ groupDetail.name?.charAt(0).toUpperCase() || 'G' }}
          </el-avatar>
        </div>
        <div class="group-info">
          <!-- 支持群主修改群名 -->
          <div v-if="isCreator && editingName" class="group-name-edit">
            <el-input 
              v-model="newGroupName" 
              @blur="handleGroupNameUpdate"
              @keyup.enter="handleGroupNameUpdate"
              placeholder="输入新群名"
              maxlength="50"
              show-word-limit
              autofocus
            />
          </div>
          <h2 v-else class="group-name" @click="isCreator && (editingName = true)">
            {{ groupDetail.name }}
            <el-icon v-if="isCreator" style="margin-left: 8px; color: #909399; cursor: pointer;">
              <Edit />
            </el-icon>
          </h2>
          <el-tag size="small">{{ groupDetail.memberCount }}人</el-tag>
        </div>
      </div>

      <!-- 基本信息卡片 -->
      <el-card class="info-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
          </div>
        </template>
        <div class="info-content">
          <p><strong>创建者:</strong> {{ groupDetail.creatorName }}</p>
          <p><strong>创建时间:</strong> {{ formatDate(getCreationTime()) }}</p>
          <p><strong>群组ID:</strong> {{ groupDetail.groupId }}</p>
        </div>
      </el-card>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <el-button type="primary" @click="$emit('invite-friend')" icon="Plus">
          邀请好友
        </el-button>
        <!-- 群主专用管理按钮 -->
        <el-button v-if="isCreator" type="warning" @click="showDissolveDialog = true" icon="Delete">
          解散群组
        </el-button>
      </div>

      <!-- 成员列表卡片 -->
      <el-card class="members-card">
        <template #header>
          <div class="card-header">
            <span>成员列表</span>
          </div>
        </template>
        
        <div class="members-list">
          <div v-for="member in groupDetail.members" :key="member.userId" class="member-item">
            <el-avatar :size="40" :src="member.avatar">
              {{ member.username?.charAt(0).toUpperCase() || 'U' }}
            </el-avatar>
            <div class="member-info">
              <div class="member-name">
                {{ member.username }}
                <el-tag 
                  v-if="member.role" 
                  size="small" 
                  :type="getRoleTagType(member.role)"
                  class="role-tag"
                >
                  {{ formatRole(member.role) }}
                </el-tag>
              </div>
              <div class="member-join-time">
                加入于: {{ formatDate(member.joinedAt) }}
              </div>
            </div>
            <!-- 群主可以踢出其他成员 -->
            <div class="member-actions" v-if="isCreator && member.role !== 'CREATOR'">
              <el-button 
                type="danger" 
                size="small" 
                @click="handleRemoveMember(member)"
                icon="Delete"
                circle
                title="移除成员"
              />
            </div>
          </div>
        </div>
      </el-card>

      <!-- 普通成员的退出群组按钮 -->
      <div v-if="!isCreator" class="exit-button-container">
        <el-button type="danger" @click="handleExitGroup" style="width: 100%; margin-top: 16px;">
          退出群组
        </el-button>
      </div>
    </template>

    <div v-else class="error-container">
      <el-empty description="无法加载群组信息" />
    </div>

    <!-- 移除成员确认对话框 -->
    <el-dialog
      v-model="removeMemberDialogVisible"
      title="移除群成员"
      width="400px"
    >
      <p>确定要将 <strong>{{ selectedMember?.username }}</strong> 移出群组吗？</p>
      <p style="color: #e6a23c; font-size: 14px;">此操作不可撤销，被移除的成员需要重新邀请才能加入。</p>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="removeMemberDialogVisible = false">取消</el-button>
          <el-button type="danger" @click="confirmRemoveMember" :loading="removing">
            确认移除
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 解散群组确认对话框 -->
    <el-dialog
      v-model="showDissolveDialog"
      title="解散群组"
      width="450px"
    >
      <div>
        <el-alert
          title="警告：解散群组是不可逆操作"
          type="error"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          <p>解散群组后：</p>
          <ul style="margin: 8px 0; padding-left: 20px;">
            <li>所有群成员将被移出群组</li>
            <li>群组聊天记录将被保留但无法继续使用</li>
            <li>此操作无法撤销</li>
          </ul>
        </el-alert>
        <p>请输入群组名称 <strong>{{ groupDetail?.name }}</strong> 来确认解散操作：</p>
        <el-input
          v-model="dissolveConfirmText"
          placeholder="请输入群组名称"
          style="margin-top: 8px;"
          @keyup.enter="confirmDissolveGroup"
        />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showDissolveDialog = false">取消</el-button>
          <el-button 
            type="danger" 
            @click="confirmDissolveGroup" 
            :loading="dissolving"
            :disabled="dissolveConfirmText !== groupDetail?.name"
          >
            确认解散
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, onUnmounted } from 'vue';
import { getGroupDetail, exitGroup, removeMember, updateGroupName, dissolveGroup } from '@/api/groupApi';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Delete, Edit } from '@element-plus/icons-vue';
import stompClientInstance from '@/net/websocket';

const props = defineProps({
  groupId: {
    type: String,
    required: true
  }
});

const emit = defineEmits(['invite-friend', 'exit-group', 'group-dissolved']);

// 状态
const loading = ref(true);
const groupDetail = ref(null);
const editingName = ref(false);
const newGroupName = ref('');
const removeMemberDialogVisible = ref(false);
const selectedMember = ref(null);
const removing = ref(false);
const showDissolveDialog = ref(false);
const dissolveConfirmText = ref('');
const dissolving = ref(false);

// 计算当前用户ID
const currentUserId = computed(() => {
  return stompClientInstance.currentUserId.value;
});

// 判断当前用户是否为群主
const isCreator = computed(() => {
  const currentUserIdNum = parseInt(currentUserId.value);
  const result = groupDetail.value && groupDetail.value.creatorId === currentUserIdNum;
  return result;
});

// 加载群组详情
const loadGroupDetail = async () => {
  loading.value = true;
  try {
    const detail = await getGroupDetail(props.groupId);
    if (detail) {
      groupDetail.value = detail;
      newGroupName.value = detail.name; // 初始化编辑用的群名
      console.log('群组详情加载成功:', detail);
    } else {
      ElMessage.error('加载群组详情失败');
    }
  } catch (error) {
    console.error('加载群组详情出错:', error);
    ElMessage.error('加载群组详情时发生错误');
  } finally {
    loading.value = false;
  }
};

// 格式化时间
const formatDate = (dateString) => {
  if (!dateString) return '未知';
  
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 格式化角色
const formatRole = (role) => {
  if (!role) return '成员';
  
  const roleMap = {
    'CREATOR': '群主',
    'ADMIN': '管理员',
    'MEMBER': '成员'
  };
  
  return roleMap[role] || role;
};

// 获取角色标签类型
const getRoleTagType = (role) => {
  if (!role) return '';
  
  const typeMap = {
    'CREATOR': 'danger',
    'ADMIN': 'warning',
    'MEMBER': 'info'
  };
  
  return typeMap[role] || '';
};

// 获取群组创建时间（使用创建者的加入时间作为群组创建时间）
const getCreationTime = () => {
  if (!groupDetail.value || !groupDetail.value.members) return null;
  
  // 查找创建者成员
  const creator = groupDetail.value.members.find(
    member => member.userId === groupDetail.value.creatorId
  );
  
  // 返回创建者的加入时间作为群组创建时间
  return creator ? creator.joinedAt : groupDetail.value.createAt;
};

// 处理群名修改
const handleGroupNameUpdate = async () => {
  if (!newGroupName.value || newGroupName.value.trim() === '') {
    ElMessage.warning('群组名称不能为空');
    newGroupName.value = groupDetail.value.name;
    editingName.value = false;
    return;
  }

  const trimmedName = newGroupName.value.trim();
  if (trimmedName === groupDetail.value.name) {
    editingName.value = false;
    return;
  }

  try {
    const success = await updateGroupName(props.groupId, trimmedName);
    if (success) {
      groupDetail.value.name = trimmedName;
      editingName.value = false;
      // 刷新群组列表
      if (window.stompClientInstance) {
        window.stompClientInstance.refreshGroups()
          .then(() => console.log('群组列表已刷新'))
          .catch(err => console.error('刷新群组列表失败:', err));
      }
    } else {
      newGroupName.value = groupDetail.value.name;
      editingName.value = false;
    }
  } catch (error) {
    console.error('更新群名失败:', error);
    newGroupName.value = groupDetail.value.name;
    editingName.value = false;
  }
};

// 处理移除成员
const handleRemoveMember = (member) => {
  selectedMember.value = member;
  removeMemberDialogVisible.value = true;
};

// 确认移除成员
const confirmRemoveMember = async () => {
  if (!selectedMember.value) return;

  removing.value = true;
  try {
    const success = await removeMember(props.groupId, selectedMember.value.userId);
    if (success) {
      // 从本地列表中移除该成员
      groupDetail.value.members = groupDetail.value.members.filter(
        m => m.userId !== selectedMember.value.userId
      );
      groupDetail.value.memberCount = groupDetail.value.members.length;
      removeMemberDialogVisible.value = false;
      selectedMember.value = null;
    }
  } catch (error) {
    console.error('移除成员失败:', error);
  } finally {
    removing.value = false;
  }
};

// 确认解散群组
const confirmDissolveGroup = async () => {
  if (dissolveConfirmText.value !== groupDetail.value?.name) {
    ElMessage.warning('请输入正确的群组名称');
    return;
  }

  dissolving.value = true;
  try {
    const success = await dissolveGroup(props.groupId, dissolveConfirmText.value);
    if (success) {
      showDissolveDialog.value = false;
      emit('group-dissolved');
      emit('exit-group'); // 触发退出事件，返回联系人页面
    }
  } catch (error) {
    console.error('解散群组失败:', error);
  } finally {
    dissolving.value = false;
  }
};

// 监听群组ID变化
watch(() => props.groupId, (newGroupId) => {
  if (newGroupId) {
    loadGroupDetail();
  }
});

// 生命周期钩子
onMounted(() => {
  if (props.groupId) {
    loadGroupDetail();
  }
  
  // 监听群组相关的WebSocket事件
  const handleGroupMemberChanged = (event) => {
    console.log('收到群组成员变化事件:', event);
    if (event.groupId === props.groupId) {
      // 重新加载群组详情
      loadGroupDetail();
    }
  };
  
  const handleGroupInfoChanged = (event) => {
    console.log('收到群组信息变化事件:', event);
    if (event.groupId === props.groupId) {
      // 更新群组名称
      if (event.newName && groupDetail.value) {
        groupDetail.value.name = event.newName;
        newGroupName.value = event.newName;
        ElMessage.success(`群组名称已更新为：${event.newName}`);
      }
    }
  };
  
  const handleGroupDissolved = (event) => {
    console.log('收到群组解散事件:', event);
    if (event.groupId === props.groupId) {
      ElMessage.warning('该群组已被解散');
      emit('group-dissolved');
      emit('exit-group');
    }
  };
  
  // 注册WebSocket事件监听
  stompClientInstance.on('onGroupMemberChanged', handleGroupMemberChanged);
  stompClientInstance.on('onGroupInfoChanged', handleGroupInfoChanged);
  stompClientInstance.on('onGroupDissolved', handleGroupDissolved);
  
  // 组件卸载时移除事件监听
  const cleanup = () => {
    stompClientInstance.off('onGroupMemberChanged', handleGroupMemberChanged);
    stompClientInstance.off('onGroupInfoChanged', handleGroupInfoChanged);
    stompClientInstance.off('onGroupDissolved', handleGroupDissolved);
  };
  
  // Vue 3的cleanup函数
  onUnmounted(() => {
    cleanup();
  });
});

// 退出群组处理
const handleExitGroup = async () => {
  try {
    const success = await exitGroup(props.groupId);
    if (success) {
      ElMessage.success('已退出群组');
      emit('exit-group');
    }
  } catch (error) {
    console.error('退出群组失败:', error);
  }
};
</script>

<style scoped>
.group-detail-panel {
  padding: 15px;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 15px;
  overflow-y: auto;
  overflow-x: hidden; /* 防止内容水平溢出 */
  box-sizing: border-box; /* 确保padding不会导致溢出 */
  width: 100%; /* 确保面板宽度占满父容器 */
}

.loading-card, .error-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%; /* 确保宽度占满 */
}

/* 确保卡片内容自适应 */
.info-card, .members-card {
  width: 100%;
  margin-bottom: 0;
  word-break: break-word; /* 确保长文本自动换行 */
  box-sizing: border-box; /* 确保边距不会导致溢出 */
}

/* 调整群组头像大小，防止挤压 */
.group-header {
  display: flex;
  align-items: center;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--border-color);
  flex-wrap: wrap; /* 允许在空间不足时换行 */
}

.group-info {
  margin-left: 15px;
  flex: 1;
}

.group-name {
  margin: 0 0 5px 0;
  font-size: 16px; /* 减小字体大小 */
  word-break: break-word; /* 确保长文字自动换行 */
  max-width: 100%; /* 确保文字不超出容器 */
  cursor: pointer;
  display: flex;
  align-items: center;
}

.group-name-edit {
  margin-bottom: 5px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}

.info-content {
  font-size: 14px;
}

.info-content p {
  margin: 8px 0;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin: 8px 0; /* 减小上下外边距 */
  flex-wrap: wrap;
}

/* 调整群组成员列表最大高度，保证在小屏幕上也能正常显示 */
.members-list {
  max-height: 40vh; /* 使用视口高度单位，更加灵活 */
  overflow-y: auto;
}

.member-item {
  display: flex;
  align-items: center;
  padding: 8px 0; /* 减小上下间距 */
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.member-item:last-child {
  border-bottom: none;
}

.member-info {
  margin-left: 10px;
  flex: 1;
}

.member-name {
  font-weight: 500;
  display: flex;
  align-items: center;
}

.role-tag {
  margin-left: 5px;
}

.member-join-time {
  font-size: 12px;
  color: #909399;
  margin-top: 3px;
}

.member-actions {
  margin-left: auto;
}

.exit-button-container {
  margin-top: auto;
}

.dialog-footer {
  text-align: right;
}
</style> 