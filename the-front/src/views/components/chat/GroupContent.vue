<template>
  <div class="group-content">
    <el-tabs v-model="groupActionTab">
      <el-tab-pane label="创建群组" name="create">
        <el-form @submit.prevent="handleCreateGroup">
          <el-form-item label="群组名称">
             <el-input v-model="newGroupName" placeholder="输入群组名称" />
          </el-form-item>
          <div class="action-buttons">
            <el-button type="primary" @click="handleCreateGroup" :loading="isCreating">创建</el-button>
          </div>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="加入群组" name="join">
         <el-form @submit.prevent="handleSearchGroup">
            <el-form-item label="搜索群组名称">
                <el-input v-model="searchGroupNameInput" placeholder="输入群组名称搜索" @keyup.enter="handleSearchGroup"/>
            </el-form-item>
            <div class="action-buttons" style="margin-bottom: 15px;">
                <el-button type="primary" @click="handleSearchGroup" :loading="isSearching">搜索</el-button>
            </div>

            <div v-if="searchedGroups.length > 0" class="search-results-container">
              <p class="results-header">搜索结果:</p>
              <div v-for="group in searchedGroups" :key="group.groupId" class="search-result-item">
                <span>{{ group.name }} (ID: {{ group.groupId }})</span>
                <el-button type="success" size="small" @click="handleJoinSpecificGroup(group)" :loading="isJoining === group.groupId">加入</el-button>
              </div>
            </div>
            <el-empty v-else-if="searchAttempted && !isSearching" description="未找到相关群组"></el-empty>
         </el-form>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { 
  createGroup as apiCreateGroup, 
  joinGroup as apiJoinGroup, 
  searchGroupByName as apiSearchGroupByName 
} from '@/api/groupApi';
import { ElMessage } from 'element-plus';

const emit = defineEmits(['group-created', 'group-joined']);

const groupActionTab = ref('create');

// 创建群组相关
const newGroupName = ref('');
const isCreating = ref(false);

// 加入群组/搜索群组相关
const searchGroupNameInput = ref(''); // 用于搜索的群组名输入
const searchedGroups = ref([]); // 存储搜索到的群组信息数组
const isSearching = ref(false); // 搜索加载状态
// isJoining 现在可以是 boolean 或 groupId，以跟踪哪个群组正在被加入
const isJoining = ref(false); 
const searchAttempted = ref(false); // 标记是否已尝试搜索

const handleCreateGroup = async () => {
  if (!newGroupName.value.trim()) {
      ElMessage.warning('请输入群组名称');
      return;
  }
  isCreating.value = true;
  try {
    const newGroup = await apiCreateGroup(newGroupName.value.trim());
    if (newGroup) {
      // ElMessage.success 已在api中提示
      emit('group-created', newGroup);
      newGroupName.value = '';
      // groupActionTab.value = 'create'; // 可选操作
    }
  } catch (error) {
    console.error('创建群组失败(组件层面):', error);
  } finally {
      isCreating.value = false;
  }
};

const handleSearchGroup = async () => {
  if (!searchGroupNameInput.value.trim()) {
    ElMessage.warning('请输入要搜索的群组名称');
    return;
  }
  isSearching.value = true;
  searchAttempted.value = true;
  searchedGroups.value = []; // 清空上次结果
  try {
    const groups = await apiSearchGroupByName(searchGroupNameInput.value.trim());
    if (groups && groups.length > 0) {
      searchedGroups.value = groups;
      ElMessage.success(`查找到 ${groups.length} 个群组`);
    } else if (groups && groups.length === 0) {
      ElMessage.info('未查找到相关群组');
    }
    // apiSearchGroupByName 内部已处理其他错误情况的 ElMessage
  } catch (error) {
    console.error('搜索群组失败(组件层面):', error);
  } finally {
    isSearching.value = false;
  }
};

// 重命名并修改，以处理特定群组的加入
const handleJoinSpecificGroup = async (groupToJoin) => {
  if (!groupToJoin || !groupToJoin.name) {
    ElMessage.warning('无效的群组信息');
    return;
  }
  isJoining.value = groupToJoin.groupId; // 设置为当前加入的群组ID，用于按钮loading
  try {
    const result = await apiJoinGroup(groupToJoin.name);
    if (result) {
      ElMessage.success(`成功请求加入群组: "${groupToJoin.name}"`);
      emit('group-joined', groupToJoin.name);
      // searchGroupNameInput.value = ''; // 保留搜索词，方便用户继续操作或查看
      // searchedGroups.value = []; // 加入成功后不清空列表，用户可能想加入多个
      // searchAttempted.value = false;
    }
  } catch (error) {
    console.error('加入群组失败(组件层面):', error);
  } finally {
    isJoining.value = false; // 重置loading状态
  }
};

// performSearchAndJoin 不再需要，因为 Enter 键现在只触发搜索
// 如果需要保留 Enter 键在表单上提交的行为，则应调用 handleSearchGroup

</script>

<style scoped>
.group-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #1f1f1f; /* 暗色背景 */
  color: #ffffff; /* 白色字体 */
  padding: 15px; /* 给整个内容区域一些内边距 */
}

/* 调整 el-tabs 样式以适应暗色主题 */
:deep(.el-tabs__nav-wrap) {
  background-color: #2d2d2d; /* Tabs 导航背景色 */
}
:deep(.el-tabs__item) {
  color: #cccccc; /* Tabs 未激活文字颜色 */
}
:deep(.el-tabs__item.is-active) {
  color: #409EFF; /* Tabs 激活文字颜色 (Element Plus 主题蓝) */
}
:deep(.el-tabs__active-bar) {
  background-color: #409EFF;
}
:deep(.el-tabs__header) {
  margin-bottom: 20px;
}

/* 表单项标签颜色 */
:deep(.el-form-item__label) {
  color: #cccccc;
}

/* 输入框样式 */
:deep(.el-input__wrapper) {
  background-color: #2d2d2d !important;
  box-shadow: none !important;
}
:deep(.el-input__inner) {
  color: #ffffff !important;
}

.action-buttons {
    display: flex;
    justify-content: flex-end;
    margin-top: 10px;
}

:deep(.el-tabs__content) {
    padding-top: 15px;
    flex: 1;
    /* background-color: #1f1f1f; /* 内容区域也用暗色 */
}

/* .search-results-container 继承父级 .group-content 的颜色设定 */
.search-results-container {
  margin-top: 20px;
  max-height: 500px; /* 修改：增大最大高度 */
  overflow-y: auto; /* 可选：内容超出时显示滚动条 */
}

.results-header {
  font-size: 1em;
  margin-bottom: 10px;
  color: #cccccc; /* 结果头部的颜色 */
}

.search-result-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 8px;
  background-color: #2d2d2d; /* 每个结果项的背景 */
  border: 1px solid #444444; /* 边框颜色调整为暗色系 */
  border-radius: 4px;
  color: #ffffff; /* 结果项中的文字颜色 */
}

.search-result-item span {
  flex-grow: 1;
  margin-right: 10px;
}

/* El-empty 描述文字颜色 */
:deep(.el-empty__description) {
  color: #909399; /* 灰色系文字 */
}

/* 按钮文字颜色，如果默认不符合主题 */
/* :deep(.el-button--primary span), :deep(.el-button--success span) ... */

</style> 