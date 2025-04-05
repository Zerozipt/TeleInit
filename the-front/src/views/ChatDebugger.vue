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
              <div v-for="(friend, index) in filteredFriends" :key="index"   
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
  </div>  
</template>  

<script>  
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue';  
import { ElMessage, ElMessageBox } from 'element-plus';  
import stompClient from '@/net/websocket';  

export default {  
  name: 'ChatApp',  
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

    // 初始化好友列表和群组列表  
    const initFriendsAndGroups = () => {  
      logEvent('info', '初始化好友和群组列表');  
      friends.length = 0;  
      
      // 检查 stompClient.friends.value 是否为数组
      if (Array.isArray(stompClient.friends.value)) {
        stompClient.friends.value.forEach(friendObj => {  
          if (friendObj && friendObj.userId && friendObj.username) {
            friends.push({  
              id: friendObj.userId,        // 使用userId作为唯一标识
              name: friendObj.username,    // 显示用的用户名
              avatar: `/api/placeholder/80/80`,  
              online: Math.random() > 0.5, // 在线状态可以后续完善
            });  
          }
        });  
      }
      logEvent('info', `加载了 ${friends.length} 个好友`);  

      groups.length = 0;  
      // 检查 stompClient.groups.value 是否为数组
      if (Array.isArray(stompClient.groups.value)) {
        stompClient.groups.value.forEach(groupObj => {  
          // 假设群组对象有 groupId 和 groupName 属性，如果实际不同，需要调整
          if (groupObj && groupObj.groupId && groupObj.groupName) { 
            groups.push({  
              id: groupObj.groupId, // 假设使用 groupId
              name: groupObj.groupName, // 假设使用 groupName
              avatar: `/api/placeholder/80/80`, // 保持模拟头像  
              memberCount: Math.floor(Math.random() * 100) + 5, // 保持模拟成员数  
            });  
          } else {
             // 如果群组数据格式不确定，可以先这样处理或记录日志
            console.warn('[ChatDebugger] Received group object with potentially missing fields, assuming default structure:', groupObj);
            // 你也可以尝试使用 groupObj.id 和 groupObj.name (如果后端返回的是这个)
            // 或者直接使用 groupObj 本身如果只需要 id
            const groupId = groupObj?.groupId || groupObj?.id || `unknown_group_${groups.length}`;
            const groupName = groupObj?.groupName || groupObj?.name || `群组 ${groupId}`;
             groups.push({
               id: groupId,
               name: groupName,
               avatar: `/api/placeholder/80/80`,
               memberCount: Math.floor(Math.random() * 100) + 5,
             });
          }
        });  
      } else {
         console.warn('[ChatDebugger] stompClient.groups.value is not an array:', stompClient.groups.value);
      }
      logEvent('info', `加载了 ${groups.length} 个群组`);  

      // 初始化聊天列表  
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

    // 加载消息（模拟）  
    const loadMessages = () => {  
      messages.value = []; // 清空现有消息  
      
      // 在实际应用中，这里应该调用API获取消息历史  
      // 现在使用模拟数据  
      if (activeChat.value) {  
        const mockMessages = [];  
        const now = new Date();  
        
        for (let i = 0; i < 10; i++) {  
          const time = new Date(now.getTime() - (10 - i) * 60000);  
          mockMessages.push({  
            id: i,  
            content: `这是一条测试消息 ${i + 1}`,  
            fromUserId: i % 3 === 0 ? stompClient.currentUserId.value : activeChat.value.id,  
            fromUser: i % 3 === 0 ? stompClient.currentUser.value : activeChat.value.name,  
            timestamp: time.toISOString(),  
            type: 'text'  
          });  
        }  
        
        messages.value = mockMessages;  
        logEvent('info', `已加载 ${mockMessages.length} 条消息历史`);  
        
        // 滚动到底部  
        nextTick(() => {  
          scrollToBottom();  
        });  
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
        // 使用activeChat.id作为接收者ID
        // 这里的activeChat.id是在selectChat方法中设置的好友userId
        const receiverId = activeChat.value.id;
        console.log(`准备发送私人消息到用户ID: ${receiverId}, 内容: ${content}`);
        
        // 调用WebSocket客户端发送私人消息
        stompClient.sendPrivateMessage(receiverId, content,activeChat.value.name);  
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
        console.log(`准备发送群组消息到群组ID: ${activeChatId.value}, 内容: ${content}`);
        
        stompClient.sendPublicMessage(content, activeChatId.value);  
        logEvent('info', `发送群组消息到 ${activeChat.value.name}: ${content}`);  
        
        // 添加到本地消息列表  
        messages.value.push({  
          id: Date.now(),  
          content: content,  
          fromUserId: stompClient.currentUserId.value,  
          fromUser: stompClient.currentUser.value,  
          timestamp: new Date().toISOString(),  
          type: 'text',
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

    // 处理接收到的私人消息  
    const handlePrivateMessage = (message) => {  
      console.log("收到私人消息:", message);
      
      const senderUserId = message.fromUserId;  // 发送者ID
      const senderUsername = message.fromUser;  // 发送者用户名
      const receiverUserId = message.toUserId;  // 接收者ID
      const content = message.content;          // 消息内容
      const currentUserId = stompClient.currentUserId.value;  // 当前用户ID
      const messageTimestamp = message.timestamp || new Date().toISOString();

      logEvent('info', `收到私人消息 - 发送者: ${senderUsername} (ID: ${senderUserId}), 接收者ID: ${receiverUserId}, 内容: ${content.substring(0, 20)}...`);  

      // 确定是否显示在当前聊天窗口中
      // 情况1：如果当前用户是发送者，且当前聊天对象是接收者
      // 情况2：如果当前用户是接收者，且当前聊天对象是发送者
      const isCurrentUserSender = currentUserId === senderUserId;
      const isCurrentUserReceiver = currentUserId === receiverUserId;
      
      // 确定要与哪个用户的聊天窗口关联此消息
      let targetUserId;
      
      if (isCurrentUserSender) {
        // 如果当前用户是发送者，应该在与接收者的聊天中显示
        targetUserId = receiverUserId;
        console.log("当前用户是发送者，消息应该显示在与接收者的聊天中, 目标用户ID:", targetUserId);
        
        // ★★★ 重要修复点 ★★★
        // 如果当前用户是发送者，检查这条消息是否已经在消息列表中
        // 这是为了避免将自己发送的消息显示两次（一次是发送时添加，一次是服务器回传时添加）
        const isMessageAlreadyAdded = messages.value.some(msg => 
          msg.content === content && 
          msg.fromUserId === senderUserId && 
          msg.toUserId === receiverUserId &&
          // 时间戳可能不完全一样，设置一个合理的时间窗口(5秒)来判断是否是同一条消息
          Math.abs(new Date(msg.timestamp) - new Date(messageTimestamp)) < 5000
        );
        
        if (isMessageAlreadyAdded) {
          console.log("检测到重复消息，忽略此消息");
          
          // 仍然更新聊天列表的最后一条消息信息
          updateChatListItem(targetUserId, content, messageTimestamp);
          return; // 不再继续处理这条消息
        }
      } else if (isCurrentUserReceiver) {
        // 如果当前用户是接收者，应该在与发送者的聊天中显示
        targetUserId = senderUserId;
        console.log("当前用户是接收者，消息应该显示在与发送者的聊天中, 目标用户ID:", targetUserId);
      } else {
        console.warn("收到的消息与当前用户无关, 忽略此消息");
        return;  // 如果既不是发送者也不是接收者，忽略此消息
      }
      
      // 检查是否是当前活跃的聊天窗口
      const isActiveChat = activeChatType.value === 'private' && activeChatId.value === targetUserId;
      
      if (isActiveChat) {
        console.log("消息将显示在当前活跃的聊天窗口中");
        // 将消息添加到当前聊天窗口
        messages.value.push({  
          id: Date.now(),  
          content: content,
          fromUserId: senderUserId,   // 保持原始发送者ID
          fromUser: senderUsername,   // 保持原始发送者用户名
          timestamp: messageTimestamp,  
          type: 'text',
          toUserId: receiverUserId    // 保持原始接收者ID
        });  

        nextTick(() => {  
          scrollToBottom();  
        });  
      } else {
        console.log("消息不在当前活跃的聊天窗口中，将更新聊天列表");
      }

      // 封装更新聊天列表项的功能为单独函数
      updateChatListItem(targetUserId, content, messageTimestamp);
    };

    // 辅助函数：更新聊天列表项
    const updateChatListItem = (targetUserId, content, timestamp) => {
      // 更新或创建聊天列表项
      let chatItem = chatList.find(c => c.id === targetUserId && c.type === 'private');  
      
      if (chatItem) {  
        // 更新现有聊天项
        chatItem.lastMessage = content;  
        chatItem.lastTime = formatTime(timestamp);  
        
        // 如果是接收消息且不是当前活跃聊天，增加未读计数
        const isActiveChat = activeChatType.value === 'private' && activeChatId.value === targetUserId;
        const isCurrentUserReceiver = stompClient.currentUserId.value !== targetUserId;
        
        if (isCurrentUserReceiver && !isActiveChat) {  
          chatItem.unread = (chatItem.unread || 0) + 1;
          console.log(`未读消息数增加为: ${chatItem.unread}`);
        }  
      } else {  
        // 聊天项不存在，需要创建新的聊天项
        // 查找目标用户的信息
        console.log("聊天项不存在，创建新的聊天项, 查找用户ID:", targetUserId);
        const targetUser = friends.find(f => f.id === targetUserId);
        
        if (targetUser) {
          console.log("找到目标用户信息:", targetUser);
          const newChatItem = {  
            id: targetUserId,  
            name: targetUser.name,  
            avatar: targetUser.avatar || '/avatar/default.png',  
            type: 'private',  
            lastMessage: content,  
            lastTime: formatTime(timestamp),  
            unread: stompClient.currentUserId.value !== targetUserId ? 1 : 0  // 只有当前用户是接收者时设置未读
          };
          
          chatList.push(newChatItem);
          console.log("已创建新的聊天项:", newChatItem);
        } else {
          console.error("找不到ID为", targetUserId, "的好友信息，无法创建聊天项");
        }
      }  
    };

    // 处理接收到的公共消息  
    const handlePublicMessage = (message) => {  
      logEvent('info', `收到群组消息，发送者: ${message.sender}，群组: ${message.groupId}`);  
      
      // 检查是否是当前聊天  
      if (activeChatType.value === 'group' && activeChatId.value === message.groupId) {  
        messages.value.push({  
          id: Date.now(),  
          content: message.content,  
          fromUserId: message.sender,  
          fromUser: message.sender,  
          timestamp: message.timestamp,  
          type: 'text'  
        });  
        
        nextTick(() => {  
          scrollToBottom();  
        });  
      }  
      
      // 更新聊天列表  
      const chatItem = chatList.find(c => c.id === message.groupId && c.type === 'group');  
      if (chatItem) {  
        chatItem.lastMessage = message.content;  
        chatItem.lastTime = formatTime(message.timestamp);  
        // 如果不是当前聊天，增加未读计数  
        if (!(activeChatType.value === 'group' && activeChatId.value === message.groupId)) {  
          chatItem.unread++;  
        }  
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

    // 生命周期钩子  
    onMounted(() => {  
      // 注册消息事件处理  
      stompClient.on('onPrivateMessage', handlePrivateMessage);  
      stompClient.on('onPublicMessage', handlePublicMessage);  
      stompClient.on('onConnected', (username) => {  
        logEvent('success', `WebSocket连接成功，用户名: ${username}`);  
        initFriendsAndGroups();  
      });  
      stompClient.on('onDisconnected', () => {  
        logEvent('info', 'WebSocket连接已断开');  
      });  
      stompClient.on('onError', (error) => {  
        logEvent('error', `WebSocket错误: ${error}`);  
        ElMessage.error(`发生错误: ${error}`);  
      });  
      
      // 添加初始日志  
      logEvent('info', '聊天应用已初始化，等待连接...');  
    });  

    onBeforeUnmount(() => {  
      // 在组件销毁前断开连接  
      if (stompClient.isConnected.value) {  
        stompClient.disconnect();  
        logEvent('info', '组件卸载：断开WebSocket连接');  
      }  
    });  

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
      sendTestMessage  
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
</style>  