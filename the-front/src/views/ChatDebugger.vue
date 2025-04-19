<template>  
  <div class="chat-container">  
    <!-- 顶部导航栏 -->  
    <el-header class="chat-header">  
      <div class="user-info">  
        <el-avatar :size="40" :src="userAvatar"></el-avatar>  
        <span class="username">{{ stompClient.currentUser.value || '未登录' }}</span>  
        <el-tag size="small" :type="stompClient.isConnected.value ? 'success' : 'danger'">  
          {{ stompClient.isConnected.value ? '在线' : '离线' }}  
        </el-tag>  
      </div>  
      <div class="header-actions">  
        <el-button v-if="!stompClient.isConnected.value" type="primary" @click="showConnectDialog" size="small">  
          <i class="el-icon-connection"></i> 连接  
        </el-button>  
        <el-button v-else type="danger" @click="disconnect" size="small">  
          <i class="el-icon-close"></i> 断开  
        </el-button>  
        <el-button type="info" @click="debugVisible = !debugVisible" size="small">  
          <i class="el-icon-monitor"></i> {{ debugVisible ? '隐藏调试' : '显示调试' }}  
        </el-button>  
      </div>  
    </el-header>  

    <el-container class="chat-body">  
      <!-- 左侧边栏 -->  
      <el-aside width="260px" class="chat-sidebar">  
        <!-- 搜索框 -->  
        <div class="search-box">  
          <el-input v-model="searchText" placeholder="搜索联系人或群组" prefix-icon="el-icon-search" clearable></el-input>  
        </div>  

        <!-- 连接状态卡片 -->  
        <div class="connection-status-card" v-if="!stompClient.isConnected.value">  
          <div class="status-icon">  
            <i class="el-icon-connection"></i>  
          </div>  
          <div class="status-text">您当前处于离线状态</div>  
          <el-button type="primary" @click="showConnectDialog" size="small">立即连接</el-button>  
        </div>

        <!-- 群组管理按钮 (新增) -->
        <div class="sidebar-actions">
          <el-button type="primary" plain size="small" @click="showGroupManagementDialog" icon="el-icon-plus" style="width: 48%;">创建群聊</el-button>
          <el-button type="success" plain size="small" @click="showGroupManagementDialog" icon="el-icon-user" style="width: 48%;">加入群聊</el-button>
        </div>

        <!-- 切换标签 -->  
        <el-tabs v-model="activeTab" class="tab-container">  
          <el-tab-pane label="聊天" name="chats">  
            <div class="chat-list-container">  
              <div v-for="(chat, index) in filteredChats" :key="index"   
                   class="chat-item"   
                   :class="{ 'active': activeChatId === chat.id && activeChatType === chat.type }"  
                   @click="selectChat(chat)">  
                <el-avatar :size="40" :src="chat.avatar"></el-avatar>  
                <div class="chat-info">  
                  <div class="chat-name">{{ chat.name }}</div>  
                  <div class="chat-message">{{ chat.lastMessage || '暂无消息' }}</div>  
                </div>  
                <div class="chat-meta">  
                  <div class="chat-time">{{ chat.lastTime || '' }}</div>  
                  <el-badge v-if="chat.unread" :value="chat.unread" class="unread-badge"></el-badge>  
                </div>  
              </div>  
            </div>  
          </el-tab-pane>  
          <el-tab-pane label="联系人" name="contacts">  
            <div class="contact-list-container">
              <!-- 添加好友按钮 -->
              <div class="add-friend-button">
                <el-button 
                  type="primary" 
                  plain 
                  size="small" 
                  @click="showAddFriendDialog" 
                  :disabled="!stompClient.isConnected.value"
                  :icon="Plus">
                  添加好友
                </el-button>
              </div>

              <!-- 好友请求列表 -->
              <div v-if="friendRequests.length > 0" class="friend-requests-section">
                <div class="section-title">好友请求 ({{friendRequests.length}})</div>
                <div v-for="request in friendRequests" :key="request.id" class="friend-request-item">
                  <el-avatar :size="40" :src="request.avatarUrl || '/api/placeholder/80/80'"></el-avatar>
                  <div class="friend-request-info">
                    <div class="friend-request-name">{{ request.fromUsername }}</div>
                    <div class="friend-request-time">{{ formatTime(request.createTime) }}</div>
                  </div>
                  <div class="friend-request-actions">
                    <el-button 
                      type="success" 
                      size="small" 
                      @click="handleAcceptFriendRequest(request)" 
                      :loading="request.loading">接受</el-button>
                    <el-button 
                      type="danger" 
                      size="small" 
                      @click="handleRejectFriendRequest(request)" 
                      :loading="request.loading">拒绝</el-button>
                  </div>
                </div>
              </div>
              
              <!-- 好友列表 -->
              <div class="section-title">我的好友 ({{friends.length}})</div>
              <div v-for="(friend, index) in filteredFriends" 
                   :key="friend.id || index"   
                   class="contact-item"  
                   :class="{ 'active': activeChatId === friend.id && activeChatType === 'private' }"  
                   @click="selectChat({id: friend.id, name: friend.name, type: 'private'})">  
                <el-avatar :size="40" :src="friend.avatar"></el-avatar>  
                <div class="contact-info">  
                  <div class="contact-name">{{ friend.name }}</div>  
                  <div class="contact-status">{{ friend.online ? '在线' : '离线' }}</div>  
                </div>  
              </div>  
            </div>
          </el-tab-pane>  
          <el-tab-pane label="群组" name="groups">  
            <div class="group-list-container">  
              <div v-for="(group, index) in filteredGroups" :key="index"   
                   class="group-item"  
                   :class="{ 'active': activeChatId === group.id && activeChatType === 'group' }"  
                   @click="selectChat({id: group.id, name: group.name, type: 'group'})">  
                <el-avatar :size="40" :src="group.avatar"></el-avatar>  
                <div class="group-info">  
                  <div class="group-name">{{ group.name }}</div>  
                  <div class="group-members">{{ group.memberCount || 0 }}人</div>  
                </div>  
              </div>  
            </div>  
          </el-tab-pane>  
        </el-tabs>  
      </el-aside>  

      <el-container v-if="!activeChat && !stompClient.isConnected.value && !debugVisible">  
        <!-- 未连接且未选择聊天时的连接提示 -->  
        <el-main class="connection-main">  
          <div class="connection-prompt">  
            <div class="connection-icon">  
              <i class="el-icon-connection"></i>  
            </div>  
            <h2>WebSocket 连接</h2>  
            <p>您需要先连接到 WebSocket 服务器才能开始聊天</p>  
            <div class="connection-form">  
              <el-input   
                v-model="jwt"   
                placeholder="请输入您的JWT令牌"   
                type="password"   
                show-password  
                class="jwt-input">  
              </el-input>  
              <div class="connection-actions">  
                <el-button @click="generateTestJwt" size="small">生成测试令牌</el-button>  
                <el-button type="primary" @click="connect" :disabled="!jwt.trim()" size="small">  
                  <i class="el-icon-connection"></i> 连接  
                </el-button>  
              </div>  
            </div>  
            <div class="connection-tips">  
              <p><i class="el-icon-warning-outline"></i> 调试提示：</p>  
              <ul>  
                <li>服务器地址: {{ SOCKET_URL }}</li>  
                <li>心跳间隔: 5秒</li>  
                <li>自动重连: 启用</li>  
              </ul>  
            </div>  
          </div>  
        </el-main>  
      </el-container>  

      <!-- 调试面板 -->  
      <el-main class="debug-panel" v-if="debugVisible">  
        <el-tabs v-model="debugTab">  
          <el-tab-pane label="连接状态" name="connection">  
            <div class="debug-section">  
              <h3>连接信息</h3>  
              <div class="debug-info">  
                <p><strong>连接状态:</strong> {{ stompClient.isConnected.value ? '已连接' : '未连接' }}</p>  
                <p><strong>当前用户:</strong> {{ stompClient.currentUser.value || '未知' }}</p>  
                <p><strong>服务器地址:</strong> {{ SOCKET_URL }}</p>  
                <p><strong>JWT令牌:</strong> <span class="jwt-display">{{ jwt ? (jwt.substring(0, 10) + '...') : '未设置' }}</span></p>  
              </div>  
              
              <div class="connection-form">  
                <el-input   
                  v-model="jwt"   
                  placeholder="请输入您的JWT令牌"   
                  type="password"   
                  show-password  
                  class="jwt-input">  
                </el-input>  
                <div class="connection-actions">  
                  <el-button @click="generateTestJwt" size="small">生成测试令牌</el-button>  
                  <el-button   
                    type="primary"   
                    @click="connect"   
                    :disabled="!jwt.trim() || stompClient.isConnected.value"   
                    size="small">  
                    <i class="el-icon-connection"></i> 连接  
                  </el-button>  
                  <el-button   
                    type="danger"   
                    @click="disconnect"   
                    :disabled="!stompClient.isConnected.value"   
                    size="small">  
                    <i class="el-icon-close"></i> 断开  
                  </el-button>  
                </div>  
              </div>  
            </div>  
            
            <div class="debug-section">  
              <h3>事件日志</h3>  
              <div class="event-log">  
                <el-scrollbar height="200px">  
                  <div v-for="(log, index) in eventLogs" :key="index" class="log-item" :class="log.type">  
                    <span class="log-time">{{ log.time }}</span>  
                    <span class="log-type">[{{ log.type.toUpperCase() }}]</span>  
                    <span class="log-message">{{ log.message }}</span>  
                  </div>  
                </el-scrollbar>  
              </div>  
              <div class="log-actions">  
                <el-button type="info" size="small" @click="clearLogs">清除日志</el-button>  
              </div>  
            </div>  
          </el-tab-pane>  
          <el-tab-pane label="好友请求" name="friendRequests">
            <div class="debug-section">
              <h3>好友请求列表</h3>
              <div class="action-bar">
                <el-button type="primary" size="small" @click="fetchFriendRequests" :disabled="!stompClient.isConnected.value">
                  <i class="el-icon-refresh"></i> 刷新请求列表
                </el-button>
              </div>
              
              <el-empty v-if="friendRequests.length === 0" description="暂无好友请求" :image-size="100"></el-empty>
              
              <el-table v-else :data="friendRequests" stripe style="width: 100%; margin-top: 15px;">
                <el-table-column prop="id" label="请求ID" width="80"></el-table-column>
                <el-table-column prop="fromUsername" label="发送者"></el-table-column>
                <el-table-column prop="createTime" label="发送时间">
                  <template #default="scope">
                    {{ formatTime(scope.row.createTime) }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="180">
                  <template #default="scope">
                    <el-button
                      type="success"
                      size="small"
                      @click="handleAcceptFriendRequest(scope.row)"
                      :loading="scope.row.loading">
                      接受
                    </el-button>
                    <el-button
                      type="danger"
                      size="small"
                      @click="handleRejectFriendRequest(scope.row)"
                      :loading="scope.row.loading">
                      拒绝
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="好友列表" name="friends">  
            <div class="debug-section">  
              <h3>好友列表</h3>  
              <el-table :data="friends" stripe style="width: 100%">  
                <el-table-column prop="id" label="ID" width="80"></el-table-column>  
                <el-table-column prop="name" label="名称"></el-table-column>  
                <el-table-column prop="online" label="状态">  
                  <template #default="scope">  
                    <el-tag :type="scope.row.online ? 'success' : 'info'">  
                      {{ scope.row.online ? '在线' : '离线' }}  
                    </el-tag>  
                  </template>  
                </el-table-column>  
                <el-table-column label="操作" width="120">  
                  <template #default="scope">  
                    <el-button   
                      type="primary"   
                      size="small"   
                      @click="selectChat({id: scope.row.id, name: scope.row.name, type: 'private'})">  
                      发送消息  
                    </el-button>  
                  </template>  
                </el-table-column>  
              </el-table>  
            </div>  
          </el-tab-pane>  
          <el-tab-pane label="群组列表" name="groups">  
            <div class="debug-section">  
              <h3>群组列表</h3>  
              <el-table :data="groups" stripe style="width: 100%">  
                <el-table-column prop="id" label="ID" width="80"></el-table-column>  
                <el-table-column prop="name" label="名称"></el-table-column>  
                <el-table-column prop="memberCount" label="成员数"></el-table-column>  
                <el-table-column label="操作" width="120">  
                  <template #default="scope">  
                    <el-button   
                      type="primary"   
                      size="small"   
                      @click="selectChat({id: scope.row.id, name: scope.row.name, type: 'group'})">  
                      进入群聊  
                    </el-button>  
                  </template>  
                </el-table-column>  
              </el-table>  
            </div>  
          </el-tab-pane>  
          <el-tab-pane label="发送测试消息" name="test">  
            <div class="debug-section">  
              <h3>发送测试消息</h3>  
              <div class="test-message-form">  
                <div class="form-item">  
                  <label>消息类型:</label>  
                  <el-radio-group v-model="testMessageType">  
                    <el-radio label="private">私人消息</el-radio>  
                    <el-radio label="group">群组消息</el-radio>  
                  </el-radio-group>  
                </div>  
                
                <div class="form-item" v-if="testMessageType === 'private'">  
                  <label>接收者:</label>  
                  <el-select v-model="testReceiver" placeholder="选择接收者">  
                    <el-option   
                      v-for="friend in friends"   
                      :key="friend.id"   
                      :label="friend.name"   
                      :value="friend.id">  
                    </el-option>  
                  </el-select>  
                </div>  
                
                <div class="form-item" v-if="testMessageType === 'group'">  
                  <label>群组:</label>  
                  <el-select v-model="testGroup" placeholder="选择群组">  
                    <el-option   
                      v-for="group in groups"   
                      :key="group.id"   
                      :label="group.name"   
                      :value="group.id">  
                    </el-option>  
                  </el-select>  
                </div>  
                
                <div class="form-item">  
                  <label>消息内容:</label>  
                  <el-input   
                    v-model="testMessage"   
                    type="textarea"   
                    :rows="3"   
                    placeholder="输入测试消息内容...">  
                  </el-input>  
                </div>  
                
                <div class="form-actions">  
                  <el-button   
                    type="primary"   
                    @click="sendTestMessage"   
                    :disabled="!stompClient.isConnected.value || !testMessage.trim()">  
                    发送测试消息  
                  </el-button>  
                </div>  
              </div>  
            </div>  
          </el-tab-pane>  
        </el-tabs>  
      </el-main>  

      <!-- 聊天主区域 -->  
      <el-main class="chat-main" v-if="activeChat && !debugVisible">  
        <div class="chat-main-header">  
          <div class="chat-title">  
            {{ activeChat.name }}  
            <span class="chat-subtitle">{{ activeChatType === 'group' ? '群聊' : '私聊' }}</span>  
          </div>  
          <div class="chat-actions">  
            <el-button icon="el-icon-more" circle plain></el-button>  
          </div>  
        </div>  

        <!-- 消息列表 -->  
        <div class="message-container" ref="messageContainer">  
          <div v-if="messages.length === 0" class="empty-message">  
            <el-empty description="暂无消息" :image-size="100"></el-empty>  
          </div>  
          <div v-else>  
            <div v-for="(message, index) in messages" :key="index" class="message-item"  
                 :class="{ 'message-mine': message.fromUserId === stompClient.currentUserId.value }">  
              <div class="message-sender" v-if="message.fromUserId !== stompClient.currentUserId.value && showSender(message, index)">  
                {{ message.fromUser }}  
              </div>  
              <div class="message-content-wrapper" :class="{ 'mine': message.fromUserId === stompClient.currentUserId.value }">  
                <el-avatar :size="36" :src="getSenderAvatar(message.fromUser, message.fromUserId)" class="message-avatar"></el-avatar>  
                <div class="message-bubble">  
                  <div class="message-content">{{ message.content }}</div>  
                  <div class="message-time">{{ formatTime(message.timestamp) }}</div>  
                </div>  
              </div>  
            </div>  
          </div>  
        </div>  

        <!-- 消息输入区域 -->  
        <div class="message-input-container">  
          <div class="input-toolbar">  
            <el-button icon="el-icon-picture-outline" circle plain></el-button>  
            <el-button icon="el-icon-paperclip" circle plain></el-button>  
            <el-button icon="el-icon-smile" circle plain></el-button>  
          </div>  
          <div class="input-area">  
            <el-input   
              v-model="messageText"   
              type="textarea"   
              :rows="3"   
              placeholder="输入消息..."   
              resize="none"  
              @keyup.enter.ctrl="sendMessage"></el-input>  
          </div>  
          <div class="send-actions">  
            <el-button size="small">按Ctrl+Enter发送</el-button>  
            <el-button type="primary" size="small" @click="sendMessage" :disabled="!messageText.trim()">发送</el-button>  
          </div>  
        </div>  
      </el-main>  

      <!-- 未选择聊天且已连接时的占位 -->  
      <el-main class="chat-empty" v-if="!activeChat && stompClient.isConnected.value && !debugVisible">  
        <div class="empty-placeholder">  
          <i class="el-icon-chat-dot-round"></i>  
          <p>选择一个联系人或群组开始聊天</p>  
          <p class="connection-status">  
            已连接到服务器，用户名: {{ stompClient.currentUser.value }}  
          </p>  
        </div>  
      </el-main>  
    </el-container>  

    <!-- 连接对话框 -->  
    <el-dialog  
      title="WebSocket 连接"  
      v-model="connectDialogVisible"  
      width="400px"  
      :close-on-click-modal="false">  
      <div class="connect-dialog-content">  
        <p>请输入您的JWT令牌以连接到WebSocket服务器</p>  
        <el-input   
          v-model="jwt"   
          placeholder="JWT令牌"   
          type="password"   
          show-password  
          class="jwt-input">  
        </el-input>  
        <div class="test-jwt-info">  
          <el-button type="text" @click="generateTestJwt" size="small">生成测试令牌</el-button>  
        </div>  
      </div>  
      <template #footer>  
        <div class="dialog-footer">  
          <el-button @click="connectDialogVisible = false">取消</el-button>  
          <el-button type="primary" @click="connect" :disabled="!jwt.trim()">连接</el-button>  
        </div>  
      </template>  
    </el-dialog>

    <!-- 群组管理对话框 (新增) -->
    <el-dialog
        title="群组管理"
        v-model="groupManagementDialogVisible"
        width="500px"
        :close-on-click-modal="false">
      <GroupManagement @group-created="onGroupCreated" @group-joined="onGroupJoined" />
      <!-- 使用 setup 语法糖，组件会自动导入 -->
    </el-dialog>

    <!-- 添加好友弹窗 -->
    <el-dialog
      title="添加好友"
      v-model="addFriendDialogVisible"
      width="30%"
      :before-close="handleAddFriendDialogClose">
      <div class="search-section">
        <el-input 
          v-model="searchQuery" 
          placeholder="输入用户名搜索"
          :prefix-icon="Search"
          @input="handleSearch"
          clearable>
        </el-input>
      </div>

      <!-- 搜索结果列表 -->
      <div v-if="searchResults.length > 0" class="search-results">
        <div v-for="user in searchResults" :key="user.id" class="search-result-item">
          <el-avatar :size="40" :src="user.avatar || '/api/placeholder/80/80'"></el-avatar>
          <div class="user-info">
            <div class="username">{{ user.username }}</div>
            <div class="user-id">ID: {{ user.id }}</div>
          </div>
          <el-button 
            type="primary" 
            size="small" 
            @click="handleAddFriend(user.id)"
            :loading="user.loading"
            :disabled="user.isAlreadyFriend">
            {{ user.isAlreadyFriend ? '已是好友' : '添加好友' }}
          </el-button>
        </div>
      </div>

      <!-- 无搜索结果提示 -->
      <el-empty 
        v-else-if="searchQuery && !isSearching" 
        description="未找到匹配的用户" 
        :image-size="100">
      </el-empty>

      <!-- 初始提示 -->
      <div v-else-if="!searchQuery" class="search-tip">
        输入用户名以搜索用户
      </div>

      <!-- 加载中状态 -->
      <div v-else-if="isSearching" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        正在搜索...
      </div>
    </el-dialog>

  </div>  
</template>  

<script>  
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch, onUnmounted } from 'vue';  
import { ElMessage, ElMessageBox } from 'element-plus';  
import stompClient from '@/net/websocket';
import GroupManagement from '@/views/GroupManagement.vue'; // 导入群组管理组件
import { generateTestJwt } from '../utils/jwt';
import { addFriend, getFriendRequests, acceptFriendRequest, rejectFriendRequest, searchUsers } from '../api/friendApi';
import { Plus, Search, Loading } from '@element-plus/icons-vue'
import { getPrivateChatHistory, getGroupChatHistory } from '../api/chatApi';

export default {  
  name: 'ChatApp',
  components: { // 注册组件 (如果不用 setup 语法糖)
    GroupManagement
  },
  setup() {  
    // 常量  
    const SOCKET_URL = 'http://localhost:8080/ws-chat'; // 与websocket.js中保持一致  

    // 用户状态  
    const userAvatar = ref('/avatar/default.png'); // 默认头像  
    //从localStorage中获取jwt,jwt的格式是：
    //{
    //  "token":"jwt",
    //  "expire":1717334400000
    //}
    //需要单独提取jwt
    const jwt = ref(''); // 初始化为空字符串
    try {
      const authData = localStorage.getItem('authorize');
      if (authData) {
        const parsedAuth = JSON.parse(authData);
        if (parsedAuth && parsedAuth.token) {
          jwt.value = parsedAuth.token;
          console.log("成功解析JWT令牌");
        } else {
          console.error("授权数据中没有token字段");
        }
      } else {
        console.warn("localStorage中未找到authorize数据");
      }
    } catch (e) {
      console.error("解析JWT令牌时出错:", e);
    }

    // 聊天状态  
    const activeTab = ref('chats');  
    const activeChatId = ref(null);  
    const activeChatType = ref(null);  
    const activeChat = ref(null);  
    const messages = ref([]);  
    const messageText = ref('');  
    const searchText = ref('');  
    const messageContainer = ref(null);  

    // 连接相关  
    const connectDialogVisible = ref(false);  
    const debugVisible = ref(false);  
    const debugTab = ref('connection');  
    const eventLogs = ref([]);  

    // 聊天列表数据  
    const chatList = reactive([]);  
    const friends = reactive([]);  
    const groups = reactive([]);  

    // 测试消息相关  
    const testMessageType = ref('private');  
    const testReceiver = ref('');  
    const testGroup = ref('');  
    const testMessage = ref('');  

    // 记录日志  
    const logEvent = (type, message) => {  
      const now = new Date();  
      const time = now.toLocaleTimeString('zh-CN');  
      eventLogs.value.push({ type, message, time });  
      // 限制日志数量，避免过多  
      if (eventLogs.value.length > 100) {  
        eventLogs.value.shift();  
      }  
    };  

    // 清除日志  
    const clearLogs = () => {  
      eventLogs.value = [];  
    };

    // --- 修改 initFriendsAndGroups (修正版) ---
    const initFriendsAndGroups = () => {
      logEvent('info', '初始化好友和群组列表');
      friends.length = 0;

      // --- 处理好友列表 (保持不变) ---
      if (Array.isArray(stompClient.friends.value)) {
        stompClient.friends.value.forEach(friendObj => {
          if (friendObj && friendObj.userId && friendObj.username) {
            friends.push({
              id: friendObj.userId,
              name: friendObj.username,
              avatar: `/api/placeholder/80/80`,
              online: false,
            });
          }
        });
      } else {
        console.warn('[ChatApp] stompClient.friends.value is not an array:', stompClient.friends.value);
      }
      logEvent('info', `加载了 ${friends.length} 个好友`);

      // --- 处理群组列表 (修改部分) ---
      groups.length = 0; // 清空本地群组列表准备重新填充

      const rawGroupData = stompClient.groups.value; // 获取原始数据 (可能是字符串)
      let parsedGroupArray = []; // 用于存放解析后的数组

      logEvent('info', `原始群组数据类型: ${typeof rawGroupData}`);
      logEvent('debug', `原始群组数据值: ${rawGroupData}`);

      // 检查是否为需要解析的字符串
      if (typeof rawGroupData === 'string' && rawGroupData.length > 0) {
        logEvent('info', '尝试将群组数据字符串解析为数组...');
        try {
          parsedGroupArray = JSON.parse(rawGroupData);
          if (!Array.isArray(parsedGroupArray)) {
            logEvent('warning', `解析后的群组数据不是一个数组! 类型: ${typeof parsedGroupArray}`);
            console.warn('[ChatApp] Parsed group data is not an array:', parsedGroupArray);
            parsedGroupArray = [];
          } else {
            logEvent('info', `成功将群组数据字符串解析为包含 ${parsedGroupArray.length} 个元素的数组.`);
          }
        } catch (error) {
          console.error('[ChatApp] 解析群组数据字符串时出错:', error);
          logEvent('error', `解析群组 JSON 字符串失败: ${error.message}`);
          parsedGroupArray = [];
        }
      } else if (Array.isArray(rawGroupData)) {
        logEvent('info', '群组数据已经是数组，直接使用.');
        parsedGroupArray = rawGroupData;
      } else {
        console.warn('[ChatApp] stompClient.groups.value 不是有效的字符串或数组:', rawGroupData);
        logEvent('warning', `无效的群组数据类型: ${typeof rawGroupData}`);
      }

      // --- 使用解析后的数组 parsedGroupArray 来填充 groups ---
      logEvent('info', `开始处理 ${parsedGroupArray.length} 个群组对象...`);
      parsedGroupArray.forEach((groupObj, index) => {
        // **修改点：** 不再查找 groupId。使用 index 作为临时 ID (主要用于可能的 UI key)
        //            并直接使用 groupname。

        // 检查是否存在 groupname
        if (groupObj && typeof groupObj.groupname !== 'undefined') {
          groups.push({
            // *** 使用索引或其他方式生成一个临时 ID (如果需要) ***
            // 例如，如果 UI 列表需要一个 key，可以用索引或 groupname+index
            id: `group_${index}`, // 使用索引生成一个简单的临时 ID
            name: groupObj.groupname, // *** 使用 groupname ***
            avatar: `/api/placeholder/80/80`,
            // 你仍然可以从 groupObj 中获取其他信息，比如 role
            role: groupObj.role, // 假设你想存储角色信息
            memberCount: groupObj.memberCount || 0, // 如果有成员数信息
          });
        } else {
          // 如果解析出的对象结构不对（例如没有 groupname）
          console.warn(`[ChatApp] 处理的第 ${index + 1} 个群组对象缺少 groupname 字段:`, groupObj);
          logEvent('warning', `第 ${index + 1} 个群组对象缺少 groupname，使用默认值。对象: ${JSON.stringify(groupObj)}`);
          groups.push({
            id: `group_unknown_${index}`, // 给个不同的临时 ID
            name: `无效群组 ${index + 1}`, // 使用默认名称
            avatar: '/api/placeholder/80/80',
            memberCount: 0,
          });
        }
      });

      logEvent('info', `本地群组列表初始化完成，数量: ${groups.length}`);

      // 更新聊天列表 (基于更新后的 friends 和 groups)
      updateChatList();
    };

    // 更新聊天列表  
    const updateChatList = () => {  
      chatList.length = 0;  
      
      // 添加好友聊天  
      friends.forEach(friend => {  
        chatList.push({  
          id: friend.id,           // 使用好友的userId
          name: friend.name,       // 使用好友的username用于显示
          avatar: friend.avatar,  
          type: 'private',  
          lastMessage: '',  
          lastTime: '',  
          unread: 0  
        });  
      });  

      // 添加群组聊天  
      groups.forEach(group => {  
        chatList.push({  
          id: group.id,  
          name: group.name,  
          avatar: group.avatar,  
          type: 'group',  
          lastMessage: '',  
          lastTime: '',  
          unread: 0  
        });  
      });  
      
      logEvent('info', `聊天列表已更新，共 ${chatList.length} 项`);  
    };  

    // 搜索过滤  
    const filteredChats = computed(() => {  
      if (!searchText.value) return chatList;  
      return chatList.filter(chat =>   
        chat.name.toLowerCase().includes(searchText.value.toLowerCase())  
      );  
    });  

    const filteredFriends = computed(() => {  
      if (!searchText.value) return friends;  
      return friends.filter(friend =>   
        friend.name.toLowerCase().includes(searchText.value.toLowerCase())  
      );  
    });  

    const filteredGroups = computed(() => {  
      if (!searchText.value) return groups;  
      return groups.filter(group =>   
        group.name.toLowerCase().includes(searchText.value.toLowerCase())  
      );  
    });  

    // 选择聊天  
    const selectChat = (chat) => {  
      console.log("选择聊天对象：", chat);
      activeChatId.value = chat.id;  // 存储选中的好友userId或群组id
      activeChatType.value = chat.type;  
      
      if (chat.type === 'private') {  
        const friend = friends.find(f => f.id === chat.id);  
        if (friend) {
          console.log("已找到好友信息：", friend);
          activeChat.value = {
            id: friend.id,           // 保存好友的userId作为聊天对象ID
            name: friend.name,       // 保存好友名称用于显示
            type: 'private',
            avatar: friend.avatar
          };
        } else {
          console.error("无法找到ID为", chat.id, "的好友");
          activeChat.value = null;
          ElMessage.error("无法找到该好友信息");
          return;
        }  
      } else {  
        const group = groups.find(g => g.id === chat.id);  
        if (group) {
          console.log("已找到群组信息：", group);
          activeChat.value = { ...group, type: 'group' };
        } else {
          console.error("无法找到ID为", chat.id, "的群组");
          activeChat.value = null;
          ElMessage.error("无法找到该群组信息");
          return;
        }  
      }  
      
      // 清除未读计数  
      const chatItem = chatList.find(c => c.id === chat.id && c.type === chat.type);  
      if (chatItem) {  
        chatItem.unread = 0;  
      }  
      
      // 加载消息历史  
      loadMessages();  
      logEvent('info', `已选择${chat.type === 'private' ? '私聊' : '群聊'}: ${chat.name}`);  
      
      // 如果在调试面板，切换回聊天界面  
      debugVisible.value = false;  
    };  

    // 加载消息历史
    const loadMessages = async () => {  
      if (!activeChat.value) return;

      messages.value = []; // 清空现有消息 
      logEvent('info', `正在加载${activeChatType.value === 'private' ? '私聊' : '群聊'}历史消息...`);
      
      try {
        let chatHistory = [];
        
        if (activeChatType.value === 'private') {
          // 获取私聊历史
          chatHistory = await getPrivateChatHistory(
            stompClient.currentUser.value, 
            activeChat.value.name,
            50
          );
        } else {
          // 获取群聊历史
          chatHistory = await getGroupChatHistory(
            activeChatId.value,
            50
          );
        }
        
        // 转换消息格式为前端统一格式
        messages.value = chatHistory.map(msg => ({
          id: msg.id || Date.now(),
          content: msg.content,
          fromUserId: msg.fromUserId || msg.senderId,
          fromUser: msg.fromUser || msg.sender,
          timestamp: msg.timestamp,
          type: activeChatType.value
        }));
        
        logEvent('success', `已加载 ${messages.value.length} 条消息历史`);
        
        // 滚动到底部  
        nextTick(() => {  
          scrollToBottom();  
        });
      } catch (error) {
        console.error('加载消息历史失败:', error);
        ElMessage.error(`加载消息历史失败: ${error.message}`);
        logEvent('error', `加载消息历史失败: ${error.message}`);
        
        // 加载失败时使用模拟数据以避免空白界面
        if (activeChat.value) {
          const mockMessages = [];
          const now = new Date();
          
          // 仅生成几个模拟消息作为备用
          for (let i = 0; i < 3; i++) {
            const time = new Date(now.getTime() - (3 - i) * 60000);
            mockMessages.push({
              id: i,
              content: `[模拟消息 ${i + 1}] 加载历史消息失败，这是备用显示`,
              fromUserId: stompClient.currentUserId.value,
              fromUser: stompClient.currentUser.value,
              timestamp: time.toISOString(),
              type: 'text'
            });
          }
          
          messages.value = mockMessages;
        }
      }
    };

    // 发送消息  
    const sendMessage = () => {  
      if (!messageText.value.trim()) return;  
      
      if (!stompClient.isConnected.value) {  
        ElMessage.warning('您当前处于离线状态，请先连接');  
        logEvent('warning', '尝试发送消息失败：未连接到服务器');  
        return;  
      }  
      
      if (!activeChat.value) {
        ElMessage.warning('请先选择聊天对象');
        return;
      }
      
      const content = messageText.value.trim();  
      
      if (activeChatType.value === 'private') {  
        // 发送私聊消息
        // 使用activeChat.id作为接收者ID
        // 这里的activeChat.id是在selectChat方法中设置的好友userId
        const receiverId = activeChat.value.id;
        console.log(`准备发送私人消息到用户ID: ${receiverId}, 内容: ${content}`);
        
        // 调用WebSocket客户端发送私人消息
        stompClient.sendPrivateMessage(receiverId, content, activeChat.value.name);  
        logEvent('info', `发送私人消息到 ${activeChat.value.name} (ID: ${receiverId}): ${content}`);  
        
        // 添加到本地消息列表
        messages.value.push({  
          id: Date.now(),  
          content: content,  
          fromUserId: stompClient.currentUserId.value,  // 自己的ID
          fromUser: stompClient.currentUser.value,      // 自己的用户名
          timestamp: new Date().toISOString(),  
          type: 'text',
          toUserId: receiverId  // 添加接收者ID
        });  
      } else if (activeChatType.value === 'group') {  
        // 发送群聊消息
        console.log(`准备发送群组消息到群组ID: ${activeChatId.value}, 内容: ${content}`);
        
        stompClient.sendPublicMessage(content, activeChatId.value);  
        logEvent('info', `发送群组消息到 ${activeChat.value.name}: ${content}`);  
        
        // 添加到本地消息列表，即时更新UI
        messages.value.push({  
          id: Date.now(),  
          content: content,  
          fromUserId: stompClient.currentUserId.value,  
          fromUser: stompClient.currentUser.value,  
          timestamp: new Date().toISOString(),  
          type: 'group', // 这里将类型标记为群组消息
          groupId: activeChatId.value
        });
      }  
      
      // 更新聊天列表中的最后一条消息  
      const chatItem = chatList.find(c => c.id === activeChatId.value && c.type === activeChatType.value);  
      if (chatItem) {  
        chatItem.lastMessage = content;
        chatItem.lastTime = formatTime(new Date().toISOString());  
      }  
      
      messageText.value = '';  
      nextTick(() => {  
        scrollToBottom();  
      });  
    };  

    // 显示连接对话框  
    const showConnectDialog = () => {  
      connectDialogVisible.value = true;  
    };  

    // 生成测试JWT  
    const generateTestJwt = () => {  
      // 这里只是生成一个模拟JWT，实际应用中应该从服务器获取  
      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));  
      const userId = Math.floor(Math.random() * 1000) + 1;  
      const payload = btoa(JSON.stringify({   
        sub: `user${userId}`,   
        name: `测试用户${userId}`,  
        iat: Math.floor(Date.now() / 1000),  
        exp: Math.floor(Date.now() / 1000) + 3600  
      }));  
      const signature = btoa('testsignature'); // 实际中应该是加密签名  
      
      jwt.value = `${header}.${payload}.${signature}`;  
      logEvent('info', '已生成测试JWT令牌');  
      ElMessage.success('已生成测试JWT令牌');  
    };  

    // 连接到WebSocket  
    const connect = async () => {  
      try {  
        if (!jwt.value.trim()) {  
          ElMessage.warning('请输入JWT令牌');  
          return;  
        }  
        
        logEvent('info', '正在连接到WebSocket服务器...');  
        connectDialogVisible.value = false;  
        
        await stompClient.connect(jwt.value);  
        ElMessage.success('连接成功');  
        logEvent('success', `连接成功，用户名: ${stompClient.currentUser.value}`);  
        initFriendsAndGroups();  
      } catch (error) {  
        const errorMsg = error.message || '未知错误';  
        ElMessage.error(`连接失败: ${errorMsg}`);  
        logEvent('error', `连接失败: ${errorMsg}`);  
      }  
    };  

    // 断开WebSocket连接  
    const disconnect = () => {  
      logEvent('info', '正在断开WebSocket连接...');  
      stompClient.disconnect();  
      ElMessage.info('已断开连接');  
      logEvent('info', '已断开WebSocket连接');  
      
      // 重置聊天状态  
      activeChatId.value = null;  
      activeChatType.value = null;  
      activeChat.value = null;  
      messages.value = [];  
      friends.length = 0;  
      groups.length = 0;  
      chatList.length = 0;  
    };  

    // 发送测试消息  
    const sendTestMessage = () => {  
      if (!stompClient.isConnected.value) {  
        ElMessage.warning('请先连接到服务器');  
        return;  
      }  
      
      if (!testMessage.value.trim()) {  
        ElMessage.warning('请输入测试消息内容');  
        return;  
      }  
      
      if (testMessageType.value === 'private') {  
        if (!testReceiver.value) {  
          ElMessage.warning('请选择接收者');  
          return;  
        }  
        
        stompClient.sendPrivateMessage(testReceiver.value, testMessage.value);  
        logEvent('info', `发送测试私人消息到用户${testReceiver.value}: ${testMessage.value}`);  
        ElMessage.success('测试私人消息已发送');  
      } else {  
        if (!testGroup.value) {  
          ElMessage.warning('请选择群组');  
          return;  
        }  
        
        stompClient.sendPublicMessage(testMessage.value, testGroup.value);  
        logEvent('info', `发送测试群组消息到群组${testGroup.value}: ${testMessage.value}`);  
        ElMessage.success('测试群组消息已发送');  
      }  
      
      // 清空测试消息  
      testMessage.value = '';  
    };  

    // 处理私人消息
    const handlePrivateMessage = (message) => {
      try {
        const messageData = typeof message === 'string' ? JSON.parse(message) : message;
        
        // 记录收到的消息
        logEvent('info', `收到私人消息 来自: ${messageData.fromUser || '未知用户'}`);
        
        // 标准化消息格式
        const formattedMessage = {
          id: Date.now(),
          content: messageData.content,
          fromUserId: messageData.fromUserId,
          fromUser: messageData.fromUser,
          timestamp: messageData.timestamp || new Date().toISOString(),
          type: 'private',
          toUserId: messageData.toUserId,
        };
        
        // 判断当前是否在与发送消息的用户聊天
        const isActiveChat = 
          activeChat.value && 
          activeChatType.value === 'private' && 
          (activeChat.value.id === messageData.fromUserId || activeChat.value.id === messageData.toUserId);
        
        // 如果当前正在与发送消息的用户聊天，则添加到消息列表
        if (isActiveChat) {
          messages.value.push(formattedMessage);
          
          // 滚动到底部
          nextTick(() => {
            scrollToBottom();
          });
        }
        
        // 更新聊天列表中的最后一条消息信息
        let chatItem;
        
        // 查找与消息关联的聊天项(可能是发送者或接收者)
        if (messageData.fromUserId === stompClient.currentUserId.value) {
          // 当前用户发送的消息
          chatItem = chatList.find(c => c.id === messageData.toUserId && c.type === 'private');
        } else {
          // 当前用户接收的消息
          chatItem = chatList.find(c => c.id === messageData.fromUserId && c.type === 'private');
        }
        
        if (chatItem) {
          chatItem.lastMessage = messageData.content;
          chatItem.lastTime = formatTime(messageData.timestamp || new Date().toISOString());
          
          // 如果不是活跃聊天，则增加未读计数
          if (!isActiveChat) {
            chatItem.unread = (chatItem.unread || 0) + 1;
          }
        }
        
        // 显示通知 (根据是否是当前聊天决定是否显示)
        if (!isActiveChat && messageData.fromUserId !== stompClient.currentUserId.value) {
          ElMessage({
            message: `${messageData.fromUser || '未知用户'}: ${messageData.content}`,
            type: 'success',
            duration: 3000
          });
        }
      } catch (error) {
        console.error('处理私人消息出错:', error);
        logEvent('error', `处理私人消息出错: ${error.message}`);
      }
    };

    // 处理公共消息
    const handlePublicMessage = (message) => {
      try {
        const messageData = typeof message === 'string' ? JSON.parse(message) : message;
        
        // 记录收到的消息
        logEvent('info', `收到群聊消息 来自: ${messageData.sender || messageData.fromUser || '未知用户'}`);
        
        // 标准化消息格式
        const formattedMessage = {
          id: Date.now(),
          content: messageData.content,
          fromUserId: messageData.senderId || messageData.fromUserId,
          fromUser: messageData.sender || messageData.fromUser,
          timestamp: messageData.timestamp || new Date().toISOString(),
          type: 'group',
          groupId: messageData.groupId
        };
        
        // 判断当前是否在查看此消息所属的群聊
        const isActiveChat = 
          activeChat.value && 
          activeChatType.value === 'group' && 
          activeChat.value.id === messageData.groupId;
        
        // 如果是当前正在查看的群聊，则添加到消息列表
        if (isActiveChat) {
          messages.value.push(formattedMessage);
          
          // 滚动到底部
          nextTick(() => {
            scrollToBottom();
          });
        }
        
        // 更新聊天列表中的最后一条消息
        const chatItem = chatList.find(c => c.id === messageData.groupId && c.type === 'group');
        if (chatItem) {
          chatItem.lastMessage = messageData.content;
          chatItem.lastTime = formatTime(messageData.timestamp || new Date().toISOString());
          
          // 如果不是活跃聊天，则增加未读计数
          if (!isActiveChat) {
            chatItem.unread = (chatItem.unread || 0) + 1;
          }
        }
        
        // 显示通知 (仅在非活跃聊天且不是自己发送的消息时)
        const isOwnMessage = formattedMessage.fromUserId === stompClient.currentUserId.value;
        if (!isActiveChat && !isOwnMessage) {
          const groupName = chatItem ? chatItem.name : '群聊';
          ElMessage({
            message: `[${groupName}] ${formattedMessage.fromUser}: ${formattedMessage.content}`,
            type: 'info',
            duration: 3000
          });
        }
      } catch (error) {
        console.error('处理群聊消息出错:', error);
        logEvent('error', `处理群聊消息出错: ${error.message}`);
      }
    };

    // 获取好友请求列表
    const fetchFriendRequests = async () => {
      if (!stompClient.isConnected.value) {
        ElMessage.warning('WebSocket未连接，无法获取好友请求');
        return;
      }
      
      try {
        const response = await fetch(`${serverUrl.value}/api/user/friend-requests`, {
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${localStorage.getItem('token')}`
          }
        });
        
        if (!response.ok) {
          throw new Error(`获取好友请求失败: ${response.status}`);
        }
        
        const data = await response.json();
        const requestsData = Array.isArray(data) ? data : (data.data || []);
        
        if (!Array.isArray(requestsData)) {
          console.error('获取到的好友请求数据不是数组:', requestsData);
          friendRequests.value = [];
          return;
        }
        
        friendRequests.value = requestsData;
        
        logEvent('info', `已加载 ${friendRequests.value.length} 个好友请求`);
      } catch (error) {
        console.error('获取好友请求出错:', error);
        ElMessage.error(`获取好友请求出错: ${error.message}`);
        logEvent('error', `获取好友请求出错: ${error.message}`);
        friendRequests.value = [];
      }
    };

    // 滚动到底部  
    const scrollToBottom = () => {  
      if (messageContainer.value) {  
        messageContainer.value.scrollTop = messageContainer.value.scrollHeight;  
      }  
    };  

    // 格式化时间  
    const formatTime = (timestamp) => {  
      if (!timestamp) return '';  
      
      const date = new Date(timestamp);  
      const now = new Date();  
      const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());  
      const yesterday = new Date(today);  
      yesterday.setDate(yesterday.getDate() - 1);  
      
      if (date >= today) {  
        // 今天的消息只显示时间  
        return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });  
      } else if (date >= yesterday) {  
        return '昨天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });  
      } else {  
        // 其他日期显示完整日期时间  
        return date.toLocaleDateString('zh-CN') + ' ' +   
               date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });  
      }  
    };  

    // 获取发送者头像  
    const getSenderAvatar = (senderUsername, senderUserId) => {  
      if (senderUserId === stompClient.currentUserId.value) {  
        return userAvatar.value;  
      }  
      
      const friend = friends.find(f => f.id === senderUserId);  
      if (friend) {  
        return friend.avatar;  
      }  
      
      return '/api/placeholder/80/80'; // 默认头像  
    };  

    // 决定是否显示发送者名称（群聊中连续消息只显示一次名称）  
    const showSender = (message, index) => {  
      if (index === 0) return true;  
      const prevMessage = messages.value[index - 1];  
      return prevMessage.fromUserId !== message.fromUserId;  
    };

    const groupManagementDialogVisible = ref(false); // 控制群组管理对话框

    // 显示群组管理对话框
    const showGroupManagementDialog = () => {
      if (!stompClient.isConnected.value) {
        ElMessage.warning('请先连接到服务器');
        return;
      }
      groupManagementDialogVisible.value = true;
    };

    // --- 事件处理：群组被创建 ---
    const onGroupCreated = (newGroup) => {
      console.log('Event received: group-created', newGroup);
      logEvent('success', `新群聊 "${newGroup.name}" 已创建`);

      // 1. 更新本地群组列表 (groups reactive array)
      //    假设 newGroup 包含 { groupId, name }
      //    注意：你的 DTO Group 有 groupId 和 name
      groups.push({
        id: newGroup.groupId, // 使用 DTO 的 groupId
        name: newGroup.name,  // 使用 DTO 的 name
        avatar: `/api/placeholder/80/80`, // 或使用后端返回的头像 URL
        memberCount: 1, // 新创建的群只有创建者
      });

      // 2. 更新聊天列表 (chatList reactive array)
      updateChatList(); // 重新生成 chatList 以包含新群组

      // 3. 关闭对话框
      groupManagementDialogVisible.value = false;

      // 4. (重要) 通知 StompClient 订阅新群组
      //    简单方式：重新获取用户信息，这会刷新所有订阅
      //    更好的方式：在 StompClientWrapper 中添加 subscribeToGroup(groupId) 方法
      //    这里使用简单方式：
      if (jwt.value) {
        console.log("重新获取用户信息以更新订阅...");
        stompClient.getUserInfByJwt(jwt.value); // 这会重新获取好友/群组并重新订阅
      } else {
        console.warn("无法重新获取用户信息，JWT 为空");
      }
    };

    // --- 事件处理：群组被加入 ---
    const onGroupJoined = async (groupname) => {
      console.log('Event received: group-joined', groupname);
      logEvent('success', `已成功加入群聊 ID: ${groupname}`);

      // 1. 检查是否已在本地群组列表中 (避免重复添加)
      const existingGroup = groups.find(g => g.name === groupname);
      if (existingGroup) {
        console.log(`群组 ${groupname} 已存在于本地列表`);
      } else {
        // 如果本地没有，需要获取群组信息来添加到列表
        // 这里假设重新获取用户信息能解决问题（包含新加入的群）
        console.log("群组不在本地列表，准备刷新用户信息...");
      }

      // 2. 关闭对话框
      groupManagementDialogVisible.value = false;

      // 3. (重要) 重新获取用户信息和订阅
      //    加入群聊后，后端 group_members 表已更新
      //    调用 getUserInfByJwt 会让后端返回包含新群组的 groupname
      //    StompClientWrapper 会根据新的 groupIds 更新 this.groups.value 并重新订阅
      if (jwt.value) {
        console.log("重新获取用户信息以更新群组列表和订阅...");
        await stompClient.getUserInfByJwt(jwt.value); // 异步等待完成
        // 等待用户信息获取完成后，确保本地列表也同步
        initFriendsAndGroups(); // 用最新的 stompClient.groups.value 更新本地 groups 数组
      } else {
        console.warn("无法重新获取用户信息，JWT 为空");
      }
    };

    // ★★★ 监听 stompClient.groups 的变化 (当 getUserInfByJwt 更新它时) ★★★
    // 这个 watch 很重要，确保 websocket.js 中 groups 更新后，ChatApp 的本地 groups 也更新
    watch(() => stompClient.groups.value, (newGroupsValue) => {
      console.log("Detected change in stompClient.groups.value, re-initializing local groups...");
      // 重新初始化本地的好友和群组列表（可能只需要更新群组，但一起更新更简单）
      initFriendsAndGroups();
    }, { deep: true }); // 使用 deep watch 以防内部结构变化

    // 添加好友相关
    const addFriendDialogVisible = ref(false);
    const addFriendForm = ref({
      username: ''
    });
    const loading = ref(false)

    const showAddFriendDialog = () => {
      if (!stompClient.isConnected.value) {
        ElMessage.warning('请先连接服务器')
        return
      }
      addFriendDialogVisible.value = true
    }

    const handleAddFriendDialogClose = () => {
      addFriendForm.value.username = ''
      addFriendDialogVisible.value = false
      loading.value = false
    }

    const handleAddFriend = async (targetUserId) => {
      if (!targetUserId) {
        ElMessage.warning('目标用户ID无效');
        return;
      }
      
      // 查找用户对象
      const user = searchResults.value.find(u => u && u.id === targetUserId);
      if (!user) {
        ElMessage.warning('未找到指定用户');
        return;
      }
      
      if (user.loading || user.isAlreadyFriend) return;
      
      user.loading = true;
      try {
        await addFriend(targetUserId);
        ElMessage.success('好友请求已发送');
        // 标记为已发送请求
        user.isAlreadyFriend = true;
      } catch (error) {
        console.error('添加好友失败:', error);
        ElMessage.error('添加好友失败：' + error.message);
      } finally {
        user.loading = false;
      }
    };

    // 生命周期钩子  
    onMounted(async () => {
      serverUrl.value = localStorage.getItem('serverUrl') || 'http://localhost:8080';
      
      try {
        // 连接到WebSocket
        await stompClient.connect(serverUrl.value);
        
        if (stompClient.isConnected.value) {
          ElMessage.success('WebSocket连接成功');
          logEvent('success', 'WebSocket连接成功');
          
          // 订阅消息
          await subscribeToMessages();
          
          // 获取好友列表和好友请求
          await fetchFriends();
          await fetchFriendRequests();
        } else {
          ElMessage.warning('WebSocket连接失败，请检查服务器状态');
          logEvent('warning', 'WebSocket连接失败，请检查服务器状态');
        }
      } catch (error) {
        console.error('WebSocket连接错误:', error);
        ElMessage.error(`WebSocket连接错误: ${error.message}`);
        logEvent('error', `WebSocket连接错误: ${error.message}`);
      }
    });

    onBeforeUnmount(() => {  
      // 在组件销毁前断开连接  
      if (stompClient.isConnected.value) {  
        stompClient.disconnect();  
        logEvent('info', '组件卸载：断开WebSocket连接');  
      }  
    });  

    // 好友请求相关
    const friendRequests = ref([]);
    
    // 接受好友请求
    const handleAcceptFriendRequest = async (request) => {
      if (!request || !request.id) {
        ElMessage.warning('无效的好友请求');
        return;
      }
      
      if (request.loading) return;
      
      try {
        request.loading = true;
        await acceptFriendRequest(request.id);
        ElMessage.success('已接受好友请求');
        logEvent('success', `已接受来自 ${request.fromUsername || '未知用户'} 的好友请求`);
        
        // 从请求列表中移除
        friendRequests.value = friendRequests.value.filter(r => r && r.id !== request.id);
        
        // 刷新好友列表
        await initFriendsAndGroups();
      } catch (error) {
        console.error('接受好友请求失败:', error);
        logEvent('error', `接受好友请求失败: ${error.message}`);
        ElMessage.error('接受好友请求失败：' + error.message);
      } finally {
        if (request) {
          request.loading = false;
        }
      }
    };
    
    // 拒绝好友请求
    const handleRejectFriendRequest = async (request) => {
      if (!request || !request.id) {
        ElMessage.warning('无效的好友请求');
        return;
      }
      
      if (request.loading) return;
      
      try {
        request.loading = true;
        await rejectFriendRequest(request.id);
        ElMessage.success('已拒绝好友请求');
        logEvent('info', `已拒绝来自 ${request.fromUsername || '未知用户'} 的好友请求`);
        
        // 从请求列表中移除
        friendRequests.value = friendRequests.value.filter(r => r && r.id !== request.id);
      } catch (error) {
        console.error('拒绝好友请求失败:', error);
        logEvent('error', `拒绝好友请求失败: ${error.message}`);
        ElMessage.error('拒绝好友请求失败：' + error.message);
      } finally {
        if (request) {
          request.loading = false;
        }
      }
    };
    
    // 监听好友请求事件
    stompClient.on('onFriendRequest', (data) => {
      // 当收到新的好友请求时，刷新请求列表
      logEvent('info', `收到来自 ${data.fromUsername} 的好友请求`);
      ElMessage.info(`收到来自 ${data.fromUsername} 的好友请求`);
      fetchFriendRequests();
    });
    
    // 好友搜索相关
    const searchQuery = ref('');
    const searchResults = ref([]);
    const isSearching = ref(false);
    const searchTimeout = ref(null);

    // 防抖处理的搜索函数
    const handleSearch = async () => {
      if (searchTimeout.value) {
        clearTimeout(searchTimeout.value);
      }
      
      if (!searchQuery.value.trim()) {
        searchResults.value = [];
        return;
      }
      
      searchTimeout.value = setTimeout(async () => {
        isSearching.value = true;
        try {
          // 获取搜索结果，如果服务器返回null或undefined，使用空数组
          const results = await searchUsers(searchQuery.value.trim()) || [];
          
          if (!Array.isArray(results)) {
            console.error('搜索结果不是数组:', results);
            searchResults.value = [];
            return;
          }
          
          // 确保currentUserId存在
          const currentUserId = stompClient.currentUserId.value;
          
          // 过滤掉自己
          const filteredResults = currentUserId 
            ? results.filter(user => user && user.id !== currentUserId) 
            : results;
            
          // 标记已经是好友的用户
          searchResults.value = filteredResults.map(user => ({
            ...user,
            loading: false,
            isAlreadyFriend: Array.isArray(friends) && friends.some(friend => friend && friend.id === user.id)
          }));
        } catch (error) {
          console.error('搜索用户失败:', error);
          ElMessage.error('搜索用户失败：' + error.message);
          searchResults.value = [];
        } finally {
          isSearching.value = false;
        }
      }, 500); // 500ms 防抖延迟
    };

    // 获取好友列表
    const fetchFriends = async () => {
      if (!stompClient.isConnected.value) {
        ElMessage.warning('WebSocket未连接，无法获取好友列表');
        return;
      }
      
      try {
        loading.value = true;
        const response = await fetch(`${serverUrl.value}/api/user/friends`, {
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${localStorage.getItem('token')}`
          }
        });
        
        if (!response.ok) {
          throw new Error(`获取好友列表失败: ${response.status}`);
        }
        
        const data = await response.json();
        const friendsData = Array.isArray(data) ? data : (data.data || []);
        
        if (!Array.isArray(friendsData)) {
          console.error('获取到的好友数据不是数组:', friendsData);
          friends.value = [];
          return;
        }
        
        friends.value = friendsData.map(friend => ({
          ...friend,
          loading: false
        }));
        
        logEvent('info', `已加载 ${friends.value.length} 个好友`);
      } catch (error) {
        console.error('获取好友列表出错:', error);
        ElMessage.error(`获取好友列表出错: ${error.message}`);
        logEvent('error', `获取好友列表出错: ${error.message}`);
        friends.value = [];
      } finally {
        loading.value = false;
      }
    };

    // 订阅消息
    const subscribeToMessages = async () => {
      // 注册消息事件处理  
      stompClient.on('onPrivateMessage', handlePrivateMessage);  
      stompClient.on('onPublicMessage', handlePublicMessage);  
      stompClient.on('onConnected', (username) => {  
        logEvent('success', `WebSocket连接成功，用户名: ${username}`);  
      });  
      stompClient.on('onDisconnected', () => {  
        logEvent('info', 'WebSocket连接已断开');  
      });  
      stompClient.on('onError', (error) => {  
        logEvent('error', `WebSocket错误: ${error}`);  
        ElMessage.error(`发生错误: ${error}`);  
      });
      stompClient.on('onFriendRequest', (data) => {
        logEvent('info', `收到来自 ${data.fromUsername} 的好友请求`);
        ElMessage.info(`收到来自 ${data.fromUsername} 的好友请求`);
        fetchFriendRequests();
      });
      
      // 添加初始日志  
      logEvent('info', '消息订阅已完成');
    };

    return {  
      // 常量  
      SOCKET_URL,  
      
      // 状态  
      stompClient,  
      userAvatar,  
      activeTab,  
      activeChatId,  
      activeChatType,  
      activeChat,  
      messages,  
      messageText,  
      searchText,  
      messageContainer,  
      connectDialogVisible,  
      debugVisible,  
      debugTab,  
      jwt,  
      eventLogs,  
      
      // 测试消息相关  
      testMessageType,  
      testReceiver,  
      testGroup,  
      testMessage,  
      
      // 数据  
      chatList,  
      friends,  
      groups,  
      
      // 计算属性  
      filteredChats,  
      filteredFriends,  
      filteredGroups,  
      
      // 方法  
      connect,  
      disconnect,  
      showConnectDialog,  
      selectChat,  
      sendMessage,  
      formatTime,  
      getSenderAvatar,  
      showSender,  
      generateTestJwt,  
      clearLogs,  
      sendTestMessage,

      // 新增
      showGroupManagementDialog,
      groupManagementDialogVisible,
      onGroupCreated, // 虽然不在模板直接用，但逻辑在此
      onGroupJoined,  // 虽然不在模板直接用，但逻辑在此
      initFriendsAndGroups, // 确保返回，因为 watch 中用到
      updateChatList, // 确保返回，因为 initFriendsAndGroups 中用到
      logEvent, // 确保返回，因为在多个地方用到

      // 新增好友相关
      addFriendDialogVisible,
      addFriendForm,
      loading,
      showAddFriendDialog,
      handleAddFriendDialogClose,
      handleAddFriend,
      Plus,

      // 好友请求相关
      friendRequests,
      fetchFriendRequests,
      handleAcceptFriendRequest,
      handleRejectFriendRequest,

      // 搜索相关
      searchQuery,
      searchResults,
      isSearching,
      handleSearch,
      Search,
      Loading,
    };  
  }  
};  
</script>  

<style scoped>  
.chat-container {  
  height: 100vh;  
  display: flex;  
  flex-direction: column;  
  background-color: #f5f7fa;  
}  

.chat-header {  
  background-color: #ffffff;  
  border-bottom: 1px solid #e6e6e6;  
  padding: 0 20px;  
  display: flex;  
  align-items: center;  
  justify-content: space-between;  
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);  
}  

.chat-body {  
  flex: 1;  
  overflow: hidden;  
}  

.user-info {  
  display: flex;  
  align-items: center;  
  gap: 10px;  
}  

.username {  
  font-weight: 500;  
  font-size: 16px;  
}  

.header-actions {  
  display: flex;  
  gap: 10px;  
}  

.chat-sidebar {  
  background-color: #ffffff;  
  border-right: 1px solid #e6e6e6;  
  display: flex;  
  flex-direction: column;  
  overflow: hidden;  
}  

.search-box {  
  padding: 15px;  
  border-bottom: 1px solid #f0f0f0;  
}  

.connection-status-card {  
  margin: 15px;  
  padding: 15px;  
  background-color: #f8f8f8;  
  border-radius: 8px;  
  text-align: center;  
  display: flex;  
  flex-direction: column;  
  align-items: center;  
  gap: 10px;  
}  

.status-icon i {  
  font-size: 30px;  
  color: #909399;  
}  

.status-text {  
  color: #606266;  
  margin-bottom: 5px;  
}  

.tab-container {  
  flex: 1;  
  display: flex;  
  flex-direction: column;  
  overflow: hidden;  
}  

.chat-list-container,  
.contact-list-container,  
.group-list-container {  
  height: calc(100vh - 220px);  
  overflow-y: auto;  
  padding: 0 10px;  
}  

.chat-item,  
.contact-item,  
.group-item {  
  display: flex;  
  align-items: center;  
  padding: 10px;  
  border-radius: 8px;  
  margin: 5px 0;  
  cursor: pointer;  
  transition: background-color 0.2s;  
}  

.chat-item:hover,  
.contact-item:hover,  
.group-item:hover {  
  background-color: #f5f7fa;  
}  

.chat-item.active,  
.contact-item.active,  
.group-item.active {  
  background-color: #ecf5ff;  
}  

.chat-info,  
.contact-info,  
.group-info {  
  flex: 1;  
  margin-left: 10px;  
  overflow: hidden;  
}  

.chat-name,  
.contact-name,  
.group-name {  
  font-weight: 500;  
  font-size: 15px;  
  white-space: nowrap;  
  overflow: hidden;  
  text-overflow: ellipsis;  
}  

.chat-message {  
  font-size: 13px;  
  color: #888;  
  white-space: nowrap;  
  overflow: hidden;  
  text-overflow: ellipsis;  
  margin-top: 3px;  
}  

.contact-status,  
.group-members {  
  font-size: 13px;  
  color: #888;  
  margin-top: 3px;  
}  

.chat-meta {  
  display: flex;  
  flex-direction: column;  
  align-items: flex-end;  
  padding-left: 10px;  
}  

.chat-time {  
  font-size: 12px;  
  color: #999;  
  margin-bottom: 5px;  
}  

.unread-badge {  
  margin-top: 5px;  
}  

.chat-main {  
  display: flex;  
  flex-direction: column;  
  padding: 0;  
  overflow: hidden;  
}  

.chat-empty,  
.connection-main {  
  display: flex;  
  align-items: center;  
  justify-content: center;  
  background-color: #f9f9f9;  
}  

.empty-placeholder {  
  text-align: center;  
  color: #909399;  
}  

.empty-placeholder i {  
  font-size: 50px;  
  margin-bottom: 10px;  
}  

.connection-status {  
  margin-top: 15px;  
  font-size: 14px;  
  color: #67c23a;  
}  

.connection-prompt {  
  max-width: 500px;  
  text-align: center;  
  padding: 30px;  
  background-color: #fff;  
  border-radius: 10px;  
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);  
}  

.connection-icon {  
  font-size: 50px;  
  color: #409eff;  
  margin-bottom: 20px;  
}  

.connection-form {  
  margin: 20px 0;  
  text-align: left;  
}  

.jwt-input {  
  margin-bottom: 15px;  
}  

.connection-actions {  
  display: flex;  
  justify-content: space-between;  
  margin-top: 15px;  
}  

.connection-tips {  
  text-align: left;  
  background-color: #f8f8f8;  
  padding: 10px 15px;  
  border-radius: 6px;  
  margin-top: 20px;  
  font-size: 13px;  
}  

.connection-tips ul {  
  padding-left: 20px;  
  margin: 5px 0;  
}  

.test-jwt-info {  
  margin-top: 5px;  
  text-align: right;  
}  

.chat-main-header {  
  padding: 15px 20px;  
  border-bottom: 1px solid #e6e6e6;  
  display: flex;  
  align-items: center;  
  justify-content: space-between;  
  background-color: #ffffff;  
}  

.chat-title {  
  font-size: 16px;  
  font-weight: 500;  
}  

.chat-subtitle {  
  font-size: 13px;  
  color: #909399;  
  margin-left: 10px;  
}  

.message-container {  
  flex: 1;  
  overflow-y: auto;  
  padding: 20px;  
  background-color: #f5f7fa;  
}  

.empty-message {  
  height: 100%;  
  display: flex;  
  align-items: center;  
  justify-content: center;  
}  

.message-item {  
  margin-bottom: 15px;  
}  

.message-sender {  
  margin-bottom: 5px;  
  font-size: 13px;  
  color: #909399;  
}  

.message-content-wrapper {  
  display: flex;  
  align-items: flex-start;  
}  

.message-content-wrapper.mine {  
  flex-direction: row-reverse;  
}  

.message-avatar {  
  margin-right: 10px;  
}  

.message-content-wrapper.mine .message-avatar {  
  margin-right: 0;  
  margin-left: 10px;  
}  

.message-bubble {  
  max-width: 70%;  
  padding: 10px 15px;  
  border-radius: 10px;  
  background-color: #ffffff;  
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);  
  position: relative;  
}  

.message-content-wrapper.mine .message-bubble {  
  background-color: #ecf5ff;  
}  

.message-content {  
  font-size: 15px;  
  word-break: break-word;  
}  

.message-time {  
  font-size: 12px;  
  color: #999;  
  margin-top: 5px;  
  text-align: right;  
}  

.message-input-container {  
  padding: 15px;  
  background-color: #ffffff;  
  border-top: 1px solid #e6e6e6;  
}  

.input-toolbar {  
  margin-bottom: 10px;  
  display: flex;  
  gap: 5px;  
}  

.input-area {  
  margin-bottom: 10px;  
}  

.send-actions {  
  display: flex;  
  justify-content: flex-end;  
  gap: 10px;  
}  

/* 调试面板样式 */  
.debug-panel {  
  padding: 20px;  
  background-color: #f9f9f9;  
  overflow-y: auto;  
}  

.debug-section {  
  margin-bottom: 30px;  
  background-color: #fff;  
  padding: 20px;  
  border-radius: 8px;  
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);  
}  

.debug-section h3 {  
  margin-top: 0;  
  margin-bottom: 15px;  
  color: #303133;  
  border-bottom: 1px solid #ebeef5;  
  padding-bottom: 10px;  
}  

.debug-info {  
  margin-bottom: 20px;  
}  

.debug-info p {  
  margin: 8px 0;  
  line-height: 1.5;  
}  

.event-log {  
  height: 200px;  
  border: 1px solid #ebeef5;  
  border-radius: 4px;  
  overflow: hidden;  
  margin-bottom: 10px;  
}  

.log-item {  
  padding: 8px 12px;  
  border-bottom: 1px solid #f5f5f5;  
  font-family: monospace;  
  font-size: 13px;  
  line-height: 1.4;  
}  

.log-item:last-child {  
  border-bottom: none;  
}  

.log-time {  
  color: #909399;  
  margin-right: 10px;  
}  

.log-type {  
  font-weight: bold;  
  margin-right: 10px;  
}  

.log-item.info .log-type {  
  color: #409eff;  
}  

.log-item.success .log-type {  
  color: #67c23a;  
}  

.log-item.warning .log-type {  
  color: #e6a23c;  
}  

.log-item.error .log-type {  
  color: #f56c6c;  
}  

.log-actions {  
  text-align: right;  
  margin-top: 10px;  
}  

.jwt-display {  
  font-family: monospace;  
  background-color: #f5f5f5;  
  padding: 2px 6px;  
  border-radius: 4px;  
}  

.test-message-form {  
  display: flex;  
  flex-direction: column;  
  gap: 15px;  
}  

.form-item {  
  display: flex;  
  flex-direction: column;  
  gap: 8px;  
}  

.form-item label {  
  font-weight: 500;  
  color: #606266;  
}  

.form-actions {  
  display: flex;  
  justify-content: flex-end;  
  margin-top: 10px;  
}

/* 新增侧边栏操作按钮样式 */
.sidebar-actions {
  padding: 10px 15px;
  display: flex;
  justify-content: space-between;
}

.add-friend-button {
  padding: 10px;
  text-align: center;
  border-bottom: 1px solid #ebeef5;
}

.add-friend-button .el-button {
  width: 100%;
}

.contact-list-container {
  height: calc(100% - 50px);
  overflow-y: auto;
}

/* 好友请求相关样式 */
.friend-requests-section {
  margin-bottom: 15px;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 10px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
  padding: 8px 10px;
  background-color: #f8f9fa;
  border-radius: 4px;
  margin: 5px 0;
}

.friend-request-item {
  display: flex;
  align-items: center;
  padding: 10px;
  border-bottom: 1px dashed #f0f0f0;
}

.friend-request-item:last-child {
  border-bottom: none;
}

.friend-request-info {
  flex: 1;
  margin-left: 10px;
  overflow: hidden;
}

.friend-request-name {
  font-weight: 500;
  font-size: 14px;
}

.friend-request-time {
  font-size: 12px;
  color: #909399;
  margin-top: 3px;
}

.friend-request-actions {
  display: flex;
  gap: 5px;
}

.search-section {
  margin-bottom: 15px;
}

.search-results {
  max-height: 200px;
  overflow-y: auto;
}

.search-result-item {
  display: flex;
  align-items: center;
  padding: 10px;
  border-bottom: 1px dashed #f0f0f0;
}

.search-result-item:last-child {
  border-bottom: none;
}

.user-info {
  flex: 1;
  margin-left: 10px;
  overflow: hidden;
}

.username {
  font-weight: 500;
  font-size: 14px;
}

.user-id {
  font-size: 12px;
  color: #909399;
}

.loading-state {
  text-align: center;
  color: #909399;
}

.search-tip {
  text-align: center;
  color: #909399;
}

</style>  