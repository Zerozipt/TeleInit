// the-front/src/net/websocket.js
import SockJS from 'sockjs-client/dist/sockjs.min.js'; // 确保路径正确
import { Stomp } from '@stomp/stompjs';
import { ref, shallowRef } from 'vue'; // 使用 shallowRef 优化 StompClient 实例
import axios from 'axios'; // 导入axios用于HTTP请求
import { getReceivedGroupInvitations } from '@/api/groupApi'; // 导入群组API
import { getAuthToken } from '@/utils/auth';

const SOCKET_URL = 'http://localhost:8080/ws-chat'; // 后端 WebSocket 端点

// 默认错误处理函数
const defaultError = (error) => {
    console.error('[StompClientWrapper] Default error handler:', error);
};

// 简单的HTTP POST请求函数
function internalPost(url, data, headers, success, failure, error = defaultError) {
    axios.post(url, data, { headers: headers }).then(({ data }) => {
        if (data.code === 200) {
            success(data.data);
        } else if (data.code === 401) {
            failure(data.message);
        } else {
            failure(data.message, data.code, url);
        }
    }).catch(err => {
        console.error('[internalPost] Request failed:', err);
        error(err);
    });
}

// 简单的HTTP GET请求函数
function internalGet(url, headers, success, failure, error = defaultError) {
    axios.get(url, { headers: headers }).then(({ data }) => {
        if (data.code === 200) {
            success(data.data);
        } else if (data.code === 401) {
            failure(data.message);
        } else {
            failure(data.message, data.code, url);
        }
    }).catch(err => {
        console.error('[internalGet] Request failed:', err);
        error(err);
    });
}

class StompClientWrapper {
    //constructor 意味着这个类被实例化时，会自动调用这个方法
    constructor() {
        this.stompClient = shallowRef(null); // 使用 shallowRef 意为这个变量是一个浅层引用，不会被监听
        this.isConnected = ref(false); // 使用 ref 意为这个变量是一个深层引用，会被监听
        this.currentUser = ref('');
        this.currentUserId = ref('');
        this.connectionPromise = null;
        this.resolveConnectionPromise = null;
        this.rejectConnectionPromise = null;
        this.subscriptions = {}; // 存储订阅，键为 destination，值为 STOMP subscription 对象
        this.heartbeatInterval = 10000; // 修复: 心跳间隔改为10秒，与后端保持一致
        this.heartbeatTimer = null; // 心跳定时器
        this.reconnectInterval = 5000; // 重连间隔,单位为毫秒
        this.reconnectAttempts = 0; // 重连尝试次数
        this.maxReconnectAttempts = 5; // 最大重连次数
        this.friends = ref([]);
        this.friendRequests = ref([]);
        this.groups = ref([]);
        // 新增群组邀请列表
        this.groupInvitations = ref([]);
        //群组消息的结构是二维数组，数组中每一项是一个Group_message对象的数组，代表一个群聊的聊天记录
        //对于这样的结构，为了方便前端显示，可以使用一个map来存储，key为群组id，value为群组消息的数组
        this.groupMessages = ref(new Map());
        //私聊消息的结构是一维数组，数组中存储的是PrivateChatMessage对象
        this.privateMessages = ref([]); 
        // 临时ID生成器（更可靠）
        this.messageSequence = 0;
        // 回调注册表
        // 注册表的实际含义是：当某个事件发生时，调用_trigger方法，触发事件，而被调用者是回调函数
        // 在此处，表现为触发事件时，将"事件"存储到注册表中，而"事件"的回调函数是注册表中的回调函数
        this.callbacks = {
            onConnected: [], // 存储回调函数，意为这个变量是一个数组，数组中存储的是回调函数
            onDisconnected: [],
            onError: [],
            onPublicMessage: [],
            onPrivateMessage: [],
            onSystemMessage: [],
            // 新增回调类型
            friendRequestsUpdated: [],
            onGroupInvitationsUpdated: [], // 新增群组邀请更新事件
            showSystemNotification: [],
            onMessageAck: [], // 新增消息确认事件
            // 新增群组事件回调类型
            onGroupMemberChanged: [], // 群组成员变化事件
            onGroupInfoChanged: [], // 群组信息变化事件
            onGroupDissolved: [] // 群组解散事件
        };
        // 添加这里 - 页面关闭事件监听
    if (typeof window !== 'undefined') {
        // 监听页面关闭事件
        window.addEventListener('beforeunload', (event) => {
            // 在页面关闭前尝试发送离线状态，直接调用disconnect方法
            this.disconnect();
        });
        
        // 监听网络状态变化
        window.addEventListener('offline', () => {
            console.log('[StompClientWrapper] 网络连接断开');
            this._trigger('onError', '网络连接已断开');
        });
        
        window.addEventListener('online', () => {
            console.log('[StompClientWrapper] 网络连接恢复');
            // 网络恢复时，检查连接状态并尝试重连
            if (!this.isConnected.value && !this.stompClient.value?.connected) {
                console.log('[StompClientWrapper] 尝试重新连接');
                this.attemptReconnect();
            }
        });
        }
    }
    
    /**
     * 生成更可靠的临时消息ID
     */
    generateTempId() {
        this.messageSequence++;
        const timestamp = Date.now();
        const sequence = this.messageSequence.toString().padStart(6, '0');
        const random = Math.random().toString(36).substr(2, 6);
        return `temp_${timestamp}_${sequence}_${random}`;
    }
    
    /**
     * 尝试重新连接
     */
    attemptReconnect() {
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('[StompClientWrapper] 已达到最大重连次数，停止重连');
            this._trigger('onError', '连接已断开，请刷新页面重新连接');
            return;
        }
        
        this.reconnectAttempts++;
        console.log(`[StompClientWrapper] 尝试第 ${this.reconnectAttempts} 次重连...`);
        
        setTimeout(() => {
            const jwt = this.getStoredJwt();
            if (jwt) {
                this.connect(jwt).catch(error => {
                    console.error('[StompClientWrapper] 重连失败:', error);
                    this.attemptReconnect();
                });
            }
        }, this.reconnectInterval * this.reconnectAttempts); // 递增延迟
    }
    
    /**
     * 获取存储的JWT（需要根据实际存储方式实现）
     */
    getStoredJwt() {
        // 这里需要根据实际的JWT存储方式来实现
        return localStorage.getItem('jwt') || sessionStorage.getItem('jwt');
    }

    // 刷新群组列表
    refreshGroups() {
        console.log('[WebSocket] refreshGroups: CALLED');
        if (!this.isConnected.value || !this.stompClient.value?.connected) {
            console.warn('[WebSocket] refreshGroups: Not connected, cannot refresh.');
            return Promise.reject(new Error('未连接到服务器'));
        }

        return new Promise((resolve, reject) => {
            try {
                // 使用统一的认证工具获取JWT
                const jwt = getAuthToken();
                if (!jwt) {
                    console.error('[WebSocket] refreshGroups: No JWT token found.');
                    return reject(new Error('用户未登录'));
                }
                
                console.log('[WebSocket] refreshGroups: Calling backend /api/groups/getGroupMembers...');
                internalPost('/api/groups/getGroupMembers',
                    null, 
                    {
                        'Authorization': 'Bearer ' + jwt
                    },
                    (responseData) => { // Renamed data to responseData for clarity, this IS the List<Group_member>
                        console.log('[WebSocket] refreshGroups: API call SUCCEEDED. Raw data received (expected List<Group_member>):', JSON.stringify(responseData));
                        
                        // Directly use responseData as it's the array of Group_member objects
                        // No longer expecting a RestBean wrapper at this stage due to internalPost's behavior
                        if (!responseData || !Array.isArray(responseData)) {
                            console.error('[WebSocket] refreshGroups: Data received is not an array or is empty/null. Received:', responseData);
                            this.groups.value = []; // Reset to empty array
                            // Resolve with empty array if data is not as expected, or it might be an empty list from backend
                            resolve([]); 
                            return;
                        }
                        
                        const groupMembers = responseData; 
                        // console.log('[WebSocket] refreshGroups: Using received data directly as groupMembers:', JSON.stringify(groupMembers)); // Optional log

                        // The rest of the transformation logic remains the same
                        try {
                            const transformedGroups = groupMembers.map(member => ({
                                groupId: member.groupId,
                                groupName: member.groupName,
                                role: member.role
                            }));
                            console.log('[WebSocket] refreshGroups: Transformed groups for UI:', JSON.stringify(transformedGroups));
                            
                            console.log('[WebSocket] refreshGroups: Current this.groups.value BEFORE update:', JSON.stringify(this.groups.value));
                            this.groups.value = transformedGroups;
                            console.log('[WebSocket] refreshGroups: Updated this.groups.value AFTER update:', JSON.stringify(this.groups.value));
                            
                            this._subscribeToPublic(); // Refresh subscriptions with potentially new groups
                            
                            resolve(this.groups.value);
                        } catch (e) {
                            console.error('[WebSocket] refreshGroups: Error processing or transforming group data:', e);
                            this.groups.value = [];
                            reject(e);
                        }
                    },
                    (errorMsg, errorStatus) => {
                        console.error(`[WebSocket] refreshGroups: API call FAILED. Status: ${errorStatus}, Message:`, errorMsg);
                        reject(new Error(errorMsg || '刷新群组列表失败'));
                    }
                );
            } catch (e) {
                console.error('[WebSocket] refreshGroups: Exception in refreshGroups method:', e);
                reject(e);
            }
        });
    }

    // 处理WebSocket获取的好友请求
    _processFriendRequests(requests) {
        if (!requests || !Array.isArray(requests)) {
            console.warn('[StompClientWrapper] 收到的好友请求格式不正确:', requests);
            return [];
        }
        
        // 获取当前用户ID
        const currentUserId = this.currentUserId.value;
        
        // 处理每个请求，标记是收到的还是发送的
        return requests.map(request => {
            const processed = { ...request };
            
            // 判断当前用户是否是接收者（secondUserId）或发送者（firstUserId）
            const isReceiver = processed.secondUserId === currentUserId.toString();
            const isSender = processed.firstUserId === currentUserId.toString();
            
            // 根据用户角色和后端状态确定前端显示状态
            if (isReceiver) {
                // 自己是接收者
                if (processed.status === 'requested') {
                    processed.displayStatus = 'requested';
                } else if (processed.status === 'accepted') {
                    processed.displayStatus = 'accepted';
                } else if (processed.status === 'rejected') {
                    processed.displayStatus = 'rejected';
                } else if (processed.status === 'deleted') {
                    processed.displayStatus = 'rejected';
                }
            } else if (isSender) {
                // 自己是发送者
                if (processed.status === 'requested') {
                    processed.displayStatus = 'sent';
                } else if (processed.status === 'accepted') {
                    processed.displayStatus = 'accepted';
                } else if (processed.status === 'rejected') {
                    processed.displayStatus = 'rejected';
                } else if (processed.status === 'deleted') {
                    processed.displayStatus = 'rejected';
                }
            }
            console.log('[StompClientWrapper] 处理好友请求成功:', processed);
            return processed;
        });
    }

    // 初始化处理好友请求列表
    _handleInitialFriendRequests(data) {
        try {
            if (data.friendRequests) {
                const parsedFriendRequests = JSON.parse(data.friendRequests) || [];
                
                // 处理并标记请求类型
                this.friendRequests.value = this._processFriendRequests(parsedFriendRequests);
                
                console.log('[StompClientWrapper] 解析好友请求列表成功, 请求数量:', this.friendRequests.value.length);
            }
        } catch (e) {
            console.error('[StompClientWrapper] 处理初始好友请求出错:', e);
        }
    }

    // 将getUserInfByJwt移到类内部作为方法
    getUserInfByJwt(jwt) {
        try {
            return internalPost('/api/chat/GetThePrivateMessage',
                null,
                {
                    'Authorization': 'Bearer ' + jwt
                },
                (data) => {
                    if (!data) {
                        console.error('[StompClientWrapper] 从服务器收到空数据');
                        return;
                    }
                    console.log('[StompClientWrapper] 获取用户信息成功:', data);
                    
                    // 使用异步处理，避免阻塞主线程
                    //{"userId":"2","username":"test2","friendIds":"[{\"userId\":\"1\",\"username\":\"test\"}]","groupIds":"[]"}
                    //获得的数据格式是json字符串，需要注意，friendIds和groupIds是json字符串，需要转换为对象
                    Promise.resolve().then(() => {
                        if (data.userId) {
                            this.currentUserId.value = data.userId;
                            console.log('[StompClientWrapper] 设置当前用户ID:', this.currentUserId.value);
                        }
                        if (data.username) {
                            this.currentUser.value = data.username;
                            console.log('[StompClientWrapper] 设置当前用户名:', this.currentUser.value);
                        }
                        
                        try {
                            if (data.friendIds) {
                                const parsedFriends = JSON.parse(data.friendIds) || [];
                                this.friends.value = parsedFriends;
                                console.log('[StompClientWrapper] 解析好友列表成功, 好友数量:', parsedFriends.length);
                            }
                            
                            if (data.groupIds) {
                                const parsedGroups = JSON.parse(data.groupIds) || [];
                                this.groups.value = parsedGroups;
                                console.log('[StompClientWrapper] 解析群组列表成功, 群组列表:', parsedGroups);
                                console.log('[StompClientWrapper] 解析群组列表成功, 群组数量:', parsedGroups.length);
                            }
                            if (data.groupMessages) {
                                // 这里后端传来的 groupMessages 是 List<List<Group_message>> 的 JSON 字符串
                                const parsedGroupMsgArrays = JSON.parse(data.groupMessages) || [];
                                // 将每个群组的消息列表按首条消息的 groupId 建立映射
                                parsedGroupMsgArrays.forEach(msgList => {
                                    if (Array.isArray(msgList) && msgList.length > 0) {
                                        const gid = String(msgList[0].groupId);
                                        this.groupMessages.value.set(gid, msgList);
                                    }
                                });
                                console.log('[StompClientWrapper] 解析群组消息映射成功:', this.groupMessages.value);
                            }
                            if(data.privateMessages){
                                const parsedPrivateMessages = JSON.parse(data.privateMessages) || [];
                                this.privateMessages.value = parsedPrivateMessages;
                                console.log('[StompClientWrapper] 解析私聊消息列表成功, 私聊消息列表:', parsedPrivateMessages);
                                console.log('[StompClientWrapper] 解析私聊消息列表成功, 私聊消息数量:', parsedPrivateMessages.length);
                            }
                            if(data.friendRequests){
                                // 使用新方法处理好友请求
                                this._handleInitialFriendRequests(data);
                            }
                        } catch (parseError) {
                            console.error('[StompClientWrapper] 解析好友或群组数据出错:', parseError);
                        }
                        
                        if (this.resolveConnectionPromise) {
                            this.resolveConnectionPromise();
                        }
                        
                        this._subscribeToPublic();
                        this._subscribeToPrivate();
                        this._subscribeToSystem();
                        this._subscribeOnlineStatus();
                        this._subscribeOfflineStatus();
                        
                        this.isConnected.value = true;
                        console.log('[StompClientWrapper] 连接状态已更新:', this.isConnected.value);
                        // 发送上线状态
                        if (this.stompClient.value) {
                            try {
                                console.log('[StompClientWrapper] Publishing online status for userId:', this.currentUserId.value, 'Type:', typeof this.currentUserId.value);
                                console.log('[StompClientWrapper] 发送上线状态');
                                this.stompClient.value.publish({
                                    destination: '/app/system/online',
                                    body: JSON.stringify({
                                        status: 'ONLINE',
                                        userId: this.currentUserId.value.toString()
                                    }),
                                    headers: {
                                        'user-id': this.currentUserId.value.toString()
                                     }
                                });
                                console.log('[StompClientWrapper] Publish call seemingly successful (frontend perspective).'); // 新增
                            } catch (publishError) {
                                console.error('[StompClientWrapper] !!! Error during stompClient.publish !!!', publishError); // 新增
                            }
                        }
                        this._trigger('onConnected', this.currentUser.value);
                        this._startHeartbeat();
                    });
                }, (error) => {
                    console.error('[StompClientWrapper] 获取用户信息失败:', error);
                    
                    if (this.rejectConnectionPromise) {
                        this.rejectConnectionPromise(error);
                    }
                    this._trigger('onError', `获取用户信息失败: ${error}`);
                });
        } catch (e) {
            console.error('[StompClientWrapper] getUserInfByJwt方法异常:', e);
            
            if (this.rejectConnectionPromise) {
                this.rejectConnectionPromise(e);
            }
            this._trigger('onError', `getUserInfByJwt方法异常: ${e.message}`);
            return Promise.reject(e);
        }
    }

    _resetConnectionPromise() { 
        // 重置连接 Promise
        this.connectionPromise = new Promise((resolve, reject) => {
            this.resolveConnectionPromise = resolve;
            this.rejectConnectionPromise = reject;
        });
    }

    // --- 事件注册 ---
    // eventName是事件名，callback是事件回调函数
    on(eventName, callback) {
        if (this.callbacks[eventName]) {
            // Optional: Check if callback already exists to prevent duplicates
            if (!this.callbacks[eventName].includes(callback)) {
                this.callbacks[eventName].push(callback);
            }
        } else {
            console.warn(`[StompClientWrapper] Unknown event name for 'on': ${eventName}`);
        }
    }

    // --- 移除事件监听 --- 
    off(eventName, callback) {
        if (this.callbacks[eventName]) {
            this.callbacks[eventName] = this.callbacks[eventName].filter(
                registeredCallback => registeredCallback !== callback
            );
        } else {
            console.warn(`[StompClientWrapper] Unknown event name for 'off': ${eventName}`);
        }
    }

    // --- 触发事件 ---
    _trigger(eventName, ...args) { 
        if (this.callbacks[eventName]) {
            this.callbacks[eventName].forEach(cb => cb(...args));
        }
    }

    // --- 连接与断开 ---
    connect(jwt) {  
        if (this.isConnected.value || (this.stompClient.value && this.stompClient.value.connected)) {  
            console.warn('[StompClientWrapper] 已连接或正在连接中');  
            return this.connectionPromise || Promise.resolve();  
        }  
    
        this._resetConnectionPromise();  
    
        try {  
            if (!jwt || !jwt.trim()) {  
                const errorMsg = '连接需要JWT令牌';  
                console.error('[StompClientWrapper] ' + errorMsg);
                this._trigger('onError', errorMsg);  
                this.rejectConnectionPromise(new Error(errorMsg));  
                return this.connectionPromise;  
            }  
    
            // 配置SockJS选项
            const sockjsOptions = {
                transports: ['websocket', 'xhr-streaming', 'xhr-polling'],
                timeout: 20000, // 20秒超时
            };
    
            // 将JWT添加为URL参数  
            console.log('[StompClientWrapper] 正在连接到WebSocket服务器，JWT长度:', jwt.length);
            const socket = new SockJS(`${SOCKET_URL}?token=${jwt}`);  
            this.stompClient.value = Stomp.over(socket);  
    
            // 配置STOMP客户端
            this.stompClient.value.configure({
                connectHeaders: {
                    token: jwt
                },
                heartbeatIncoming: 10000, // 10秒心跳间隔，与后端一致
                heartbeatOutgoing: 10000, // 10秒心跳间隔，与后端一致
                reconnectDelay: 5000,
            });
    
            // 启用STOMP调试信息以便排查问题
            this.stompClient.value.debug = (str) => { 
                // 可以在开发环境打开这个日志
                if (str.includes('ERROR') || str.includes('WARN')) {
                    console.log('[STOMP Debug]', str); 
                }
            };  
    
            // STOMP连接
            this.stompClient.value.connect(  
                {
                    token: jwt,
                    'heart-beat': '10000,10000' // 与后端一致的10秒心跳
                },
                //修改连接逻辑，连接成功后，向后端发送get请求，获取用户信息
                (frame) => {
                    console.log('[StompClientWrapper] 已连接到服务器:', frame);
                    // 不要尝试直接设置connected属性，它是只读的
                    console.log('[StompClientWrapper] 使用JWT获取用户数据');
                    // WebSocket连接成功后，获取用户信息
                    this.getUserInfByJwt(jwt);
                    
                },
                (error) => {  
                    console.error('[StompClientWrapper] 连接错误:', error);  
                    const errorMessage = error.headers?.message || error.message || '连接失败';  
                    this.isConnected.value = false;  
                    this.currentUser.value = '';  
                    this._trigger('onError', errorMessage);  
                    this.rejectConnectionPromise(error);  
                    this._cleanup();  
                }  
            );  
        } catch (err) {  
            console.error('[StompClientWrapper] 连接设置异常:', err);  
            this.isConnected.value = false;  
            this._trigger('onError', `连接异常: ${err.message}`);  
            this.rejectConnectionPromise(err);
            this._cleanup();
        }  
        return this.connectionPromise;  
    }  

    disconnect() {
        if (this.stompClient.value && this.stompClient.value.connected) {
            try {
                // 发送离线状态通知
                this.stompClient.value.publish({
                    destination: '/app/system/offline',
                    body: JSON.stringify({
                        status: 'OFFLINE',
                        userId: this.currentUserId.value
                    }),
                    headers: {
                        'user-id': this.currentUserId.value
                    }
                });
                
                // 给服务器一点时间处理离线消息
                setTimeout(() => {
                    // 取消所有订阅
                    Object.values(this.subscriptions).forEach(sub => sub?.unsubscribe());
                    this.subscriptions = {};
    
                    this.stompClient.value.disconnect(() => {
                        console.log('[StompClientWrapper] Disconnected.');
                        this._cleanup();
                        this._trigger('onDisconnected');
                    });
                }, 200); // 短暂延迟确保离线消息被发送
            } catch (e) {
                console.error('[StompClientWrapper] 发送离线状态失败:', e);
                this._cleanup();
            }
        } else {
           this._cleanup(); // 确保状态被重置
        }
    }
    
    _startHeartbeat() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
        }
        this.heartbeatTimer = setInterval(() => {
            if (this.stompClient.value && this.stompClient.value.connected) {
                this.stompClient.value.publish({
                    destination: '/app/chat/heartbeat',
                    headers: {
                        'user-name': this.currentUser.value,
                        'user-id': this.currentUserId.value
                    }
                });
            }
        }, this.heartbeatInterval);
    }


    _cleanup() {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
        
        if (this.stompClient.value) {
            try {
                Object.values(this.subscriptions).forEach(sub => {
                    try {
                        sub?.unsubscribe();
                    } catch (e) {
                        console.warn('[StompClientWrapper] Error unsubscribing:', e);
                    }
                });
            } catch (e) {
                console.warn('[StompClientWrapper] Error cleaning up subscriptions:', e);
            }
        }

        this.stompClient.value = null;
        this.isConnected.value = false;
        this.currentUser.value = '';
        this.subscriptions = {};
        this.connectionPromise = null;
        this.resolveConnectionPromise = null;
        this.rejectConnectionPromise = null;
        this.friends.value = [];
        this.groups.value = [];
        this.friendRequests.value = [];
    }

    // --- 订阅 ---
    _subscribeToPublic() {
        // 清理已失效的订阅
        Object.keys(this.subscriptions).forEach(dest => {
            if (dest.startsWith('/topic/group/')) {
                const groupId = dest.replace('/topic/group/', '');
                const groupExists = this.groups.value.some(group => group.groupId === groupId);
                if (!groupExists) {
                    console.log('[StompClientWrapper] 清理无效群组订阅:', dest);
                    this.subscriptions[dest]?.unsubscribe();
                    delete this.subscriptions[dest];
                }
            }
        });
        
        // 为当前群组订阅消息频道
        this.groups.value.forEach(group => {
            const destination = '/topic/group/' + group.groupId; // 修复: 统一路径格式
            console.log('[StompClientWrapper] 检查群组订阅:', destination);
            
            // 如果订阅不存在，并且连接成功，则订阅
            if (!this.subscriptions[destination] && this.stompClient.value?.connected) {
                try {
                    console.log('[StompClientWrapper] 正在订阅群组消息频道:', destination);
                    this.subscriptions[destination] = this.stompClient.value.subscribe(destination, (message) => {
                        try {
                            const parsedMessage = JSON.parse(message.body);
                            console.log('[StompClientWrapper] 收到群组消息:', parsedMessage);
                            
                            // 确保消息包含必要字段
                            if (!parsedMessage.senderId || !parsedMessage.content) {
                                console.warn('[StompClientWrapper] 群组消息缺少必要字段:', parsedMessage);
                                return;
                            }
                            
                            // 触发群组消息事件
                            this._trigger('onPublicMessage', parsedMessage);
                        } catch (e) {
                            console.error('[StompClientWrapper] 解析群组消息失败:', e, message.body);
                        }
                    });
                    
                    console.log(`[StompClientWrapper] 成功订阅群组频道: ${destination}`);
                } catch(e) {
                    console.error(`[StompClientWrapper] 订阅群组频道失败: ${destination}`, e);
                    this._trigger('onError', `订阅群组频道失败: ${e.message}`);
                }
            } else if (this.subscriptions[destination]) {
                console.log(`[StompClientWrapper] 群组频道已订阅: ${destination}`);
            } else {
                console.warn(`[StompClientWrapper] 无法订阅群组频道，STOMP客户端未连接: ${destination}`);
            }
        });
    }
    
    _subscribeOnlineStatus() {
        const onlineDest = '/user/queue/online';
        if (!this.subscriptions[onlineDest] && this.stompClient.value?.connected) {
            this.subscriptions[onlineDest] = this.stompClient.value.subscribe(onlineDest, (message) => {
                const statusUpdate = JSON.parse(message.body);
                console.log('[StompClientWrapper] User Online:', statusUpdate);
                
                // 更新好友在线状态
                const userId = statusUpdate.userId;
                if (this.friends.value && this.friends.value.length > 0) {
                    this.friends.value.forEach(friend => {
                        if (friend.firstUserId === userId || friend.secondUserId === userId) {
                            friend.online = true;
                            console.log(`[StompClientWrapper] 设置好友 ${userId} 在线状态为: 在线`);
                        }
                    });
                }
                
                // 触发事件通知
                this._trigger('onUserOnline', statusUpdate); 
            });
        }
    }

    _subscribeOfflineStatus() {
       const offlineDest = '/user/queue/offline';
        if (!this.subscriptions[offlineDest] && this.stompClient.value?.connected) {
            this.subscriptions[offlineDest] = this.stompClient.value.subscribe(offlineDest, (message) => {
                const statusUpdate = JSON.parse(message.body);
                console.log('[StompClientWrapper] User Offline:', statusUpdate);
                
                // 更新好友离线状态
                const userId = statusUpdate.userId;
                if (this.friends.value && this.friends.value.length > 0) {
                    this.friends.value.forEach(friend => {
                        if (friend.firstUserId === userId || friend.secondUserId === userId) {
                            friend.online = false;
                            console.log(`[StompClientWrapper] 设置好友 ${userId} 在线状态为: 离线`);
                        }
                    });
                }
                
                // 触发事件通知
                this._trigger('onUserOffline', statusUpdate);
            });
        }
    }

    _subscribeToPrivate() { 
        const destination = '/user/queue/private'; // 用户需要订阅这个地址来接收私信
        if (!this.subscriptions[destination] && this.stompClient.value?.connected) {
            try {
                console.log(`[StompClientWrapper] 正在订阅私人消息频道 ${destination}，当前用户ID: ${this.currentUserId.value}`);
                
                this.subscriptions[destination] = this.stompClient.value.subscribe(destination, (message) => {
                    try {
                        console.log('[StompClientWrapper] 收到原始私人消息:', message);
                        const parsedMessage = JSON.parse(message.body);
                        console.log('[StompClientWrapper] 解析后的私人消息:', parsedMessage);
                        
                        // 确保消息包含必要字段
                        if (!parsedMessage.senderId || !parsedMessage.content) {
                            console.warn('[StompClientWrapper] 接收到的私人消息缺少必要字段:', parsedMessage);
                        }
                        
                        // 触发消息接收事件
                        this._trigger('onPrivateMessage', parsedMessage);
                    } catch (e) {
                        console.error('[StompClientWrapper] 解析私人消息出错:', e, message.body);
                    }
                });
                
                console.log(`[StompClientWrapper] 已成功订阅私人消息频道 ${destination}`);
            } catch(e) {
                console.error(`[StompClientWrapper] 订阅 ${destination} 失败:`, e);
                this._trigger('onError', `订阅私人消息频道失败: ${e.message}`);
            }
        } else if (this.subscriptions[destination]) {
            console.log(`[StompClientWrapper] 已经订阅了 ${destination}，无需重复订阅`);
        } else {
            console.warn(`[StompClientWrapper] 无法订阅 ${destination}，STOMP客户端未连接`);
        }
    }

    _subscribeToSystem() {
        const destination = '/user/queue/system'; // 系统消息队列，删除用户ID拼接
        if (!this.subscriptions[destination] && this.stompClient.value?.connected) {
            try {
                console.log(`[StompClientWrapper] 正在订阅系统消息频道 ${destination}，当前用户ID: ${this.currentUserId.value}`);
                
                this.subscriptions[destination] = this.stompClient.value.subscribe(destination, (message) => {
                    try {
                        console.log('[StompClientWrapper] 收到系统消息:', message);
                        const parsedMessage = JSON.parse(message.body);
                        console.log('[StompClientWrapper] 解析后的系统消息:', parsedMessage);
                        
                        // 处理不同类型的系统消息
                        if (parsedMessage.type === 'friendRequest') {
                            // 收到好友请求，添加到好友请求列表
                            this._handleFriendRequest(parsedMessage);
                        } else if (parsedMessage.type === 'friendAccept') {
                            // 收到好友接受请求的通知
                            this._handleFriendAccepted(parsedMessage);
                        } else if (parsedMessage.type === 'friendRequestRejected') {
                            this._handleFriendRequestRejected(parsedMessage);
                        } else if (parsedMessage.type === 'friendRequestCancelledBySender') {
                            this._handleFriendRequestCancelledBySender(parsedMessage);
                        } else if (parsedMessage.type === 'groupInvite') {
                            this._handleGroupInvite(parsedMessage);
                        } else if (parsedMessage.type === 'friendRequestSent') {
                            // 自己发送的好友请求消息
                            this._handleFriendRequestSent(parsedMessage);
                        }
                        
                        // 触发系统消息事件
                        this._trigger('onSystemMessage', parsedMessage);
                    } catch (e) {
                        console.error('[StompClientWrapper] 解析系统消息出错:', e, message.body);
                    }
                });
                
                console.log(`[StompClientWrapper] 已成功订阅系统消息频道 ${destination}`);
            } catch(e) {
                console.error(`[StompClientWrapper] 订阅 ${destination} 失败:`, e);
                this._trigger('onError', `订阅系统消息频道失败: ${e.message}`);
            }
        } else if (this.subscriptions[destination]) {
            console.log(`[StompClientWrapper] 已经订阅了 ${destination}，无需重复订阅`);
        } else {
            console.warn(`[StompClientWrapper] 无法订阅 ${destination}，STOMP客户端未连接`);
        }
        
        // 添加个人通知频道订阅 - 用于群聊管理通知
        const notificationDestination = '/user/queue/notifications';
        if (!this.subscriptions[notificationDestination] && this.stompClient.value?.connected) {
            try {
                console.log(`[StompClientWrapper] 正在订阅个人通知频道 ${notificationDestination}`);
                
                this.subscriptions[notificationDestination] = this.stompClient.value.subscribe(notificationDestination, (message) => {
                    try {
                        console.log('[StompClientWrapper] 收到个人通知:', message);
                        const parsedMessage = JSON.parse(message.body);
                        console.log('[StompClientWrapper] 解析后的个人通知:', parsedMessage);
                        
                        // 处理群组管理通知
                        this._handleGroupNotification(parsedMessage);
                        
                        // 触发通知事件
                        this._trigger('showSystemNotification', {
                            title: this._getNotificationTitle(parsedMessage.type),
                            message: parsedMessage.message || '您有新的通知',
                            type: this._getNotificationType(parsedMessage.type)
                        });
                    } catch (e) {
                        console.error('[StompClientWrapper] 解析个人通知出错:', e, message.body);
                    }
                });
                
                console.log(`[StompClientWrapper] 已成功订阅个人通知频道 ${notificationDestination}`);
            } catch(e) {
                console.error(`[StompClientWrapper] 订阅 ${notificationDestination} 失败:`, e);
                this._trigger('onError', `订阅个人通知频道失败: ${e.message}`);
            }
        }
        
        // 添加消息确认队列订阅
        const ackDestination = '/user/queue/message-ack';
        if (!this.subscriptions[ackDestination] && this.stompClient.value?.connected) {
            try {
                console.log(`[StompClientWrapper] 正在订阅消息确认频道 ${ackDestination}`);
                
                this.subscriptions[ackDestination] = this.stompClient.value.subscribe(ackDestination, (message) => {
                    try {
                        console.log('[StompClientWrapper] 收到消息确认:', message);
                        const ackData = JSON.parse(message.body);
                        console.log('[StompClientWrapper] 解析后的消息确认:', ackData);
                        
                        // 触发消息确认事件
                        this._trigger('onMessageAck', ackData);
                    } catch (e) {
                        console.error('[StompClientWrapper] 解析消息确认出错:', e, message.body);
                    }
                });
                
                console.log(`[StompClientWrapper] 已成功订阅消息确认频道 ${ackDestination}`);
            } catch(e) {
                console.error(`[StompClientWrapper] 订阅 ${ackDestination} 失败:`, e);
                this._trigger('onError', `订阅消息确认频道失败: ${e.message}`);
            }
        }
    }

    // 处理好友请求
    _handleFriendRequest(message) {
        // 确保friendRequests已初始化
        if (!this.friendRequests.value) {
            this.friendRequests.value = [];
        }
        
        // 获取当前用户ID
        const currentUserId = this.currentUserId.value;
        
        // 检查是否包含完整的FriendsResponse对象
        if (message.friendsResponse) {
            // 使用后端提供的FriendsResponse对象
            const friendRequest = { ...message.friendsResponse };
            
            // 判断当前用户是否是接收者（secondUserId）或发送者（firstUserId）
            const isReceiver = friendRequest.secondUserId === currentUserId.toString();
            const isSender = friendRequest.firstUserId === currentUserId.toString();
            
            // 根据用户角色和后端状态确定前端显示状态
            if (isReceiver) {
                // 自己是接收者
                if (friendRequest.status === 'requested') {
                    // 收到的请求，保持'requested'状态
                    friendRequest.displayStatus = 'requested';
                } else if (friendRequest.status === 'accepted') {
                    // 已接受的请求
                    friendRequest.displayStatus = 'accepted';
                } else if (friendRequest.status === 'rejected') {
                    // 已拒绝的请求
                    friendRequest.displayStatus = 'rejected';
                } else if (friendRequest.status === 'deleted') {
                    // If backend sends 'deleted' for a request (e.g. hard delete), 
                    // we might still want to show it as 'rejected' or filter it out.
                    // For now, let's also map it to 'rejected' to ensure it's handled consistently with old logic if 'deleted' is still somehow sent for requests.
                    friendRequest.displayStatus = 'rejected'; 
                }
            } else if (isSender) {
                // 自己是发送者
                if (friendRequest.status === 'requested') {
                    // 自己发送的等待回应的请求
                    friendRequest.displayStatus = 'sent';
                } else if (friendRequest.status === 'accepted') {
                    // 对方已接受的请求
                    friendRequest.displayStatus = 'accepted';
                } else if (friendRequest.status === 'rejected') {
                    // 被拒绝或已取消的请求
                    friendRequest.displayStatus = 'rejected';
                } else if (friendRequest.status === 'deleted') {
                    friendRequest.displayStatus = 'rejected'; // Consistent handling for 'deleted'
                }
            }
            
            // 检查是否已存在相同的请求
            const exists = this.friendRequests.value.some(req => 
                req.firstUserId === friendRequest.firstUserId && 
                req.secondUserId === friendRequest.secondUserId
            );
            
            // 如果不存在，添加到列表
            if (!exists) {
                this.friendRequests.value.push(friendRequest);
                console.log('[StompClientWrapper] 添加新好友请求:', friendRequest);
            }
        } else {
            // 兼容旧格式：手动构建FriendsResponse对象
            const friendRequest = {
                firstUserId: message.senderId?.toString(),
                secondUserId: this.currentUserId.value,
                firstUsername: message.senderUsername,
                secondUsername: this.currentUser.value,
                created_at: new Date(message.timestamp),
                status: 'requested', // 旧格式都是收到的请求
                displayStatus: 'requested' // 前端显示状态也是'requested'
            };
            
            // 检查是否已存在相同的请求
            const exists = this.friendRequests.value.some(req => 
                req.firstUserId === friendRequest.firstUserId && 
                req.secondUserId === friendRequest.secondUserId
            );
            
            // 如果不存在，添加到列表
            if (!exists) {
                this.friendRequests.value.push(friendRequest);
                console.log('[StompClientWrapper] 添加新好友请求(旧格式):', friendRequest);
            }
        }
    }

    // 处理好友请求被接受的消息
    _handleFriendAccepted(message) {
        try {
            console.log('[StompClientWrapper] 收到好友接受通知:', message);
            console.log('[StompClientWrapper] 当前好友列表:', this.friends.value);
            console.log('[StompClientWrapper] 当前好友请求列表:', this.friendRequests.value);
            
            // 先从本地好友请求列表中移除已接受的请求
            if (message.friendsResponse) {
                const response = message.friendsResponse;
                // 使用firstUserId(发送者)和secondUserId(接收者)过滤
                this.friendRequests.value = this.friendRequests.value.filter(req => 
                    !(req.firstUserId === response.firstUserId && 
                      req.secondUserId === response.secondUserId)
                );
                console.log('[StompClientWrapper] 本地移除已接受的好友请求，更新后的请求列表:', this.friendRequests.value);
            }
            
            // 使用后端API刷新好友列表和好友请求，而不是本地更新
            // 刷新好友列表
            this.refreshFriends()
                .then((updatedFriends) => {
                    console.log('[StompClientWrapper] 好友列表已刷新，新列表:', updatedFriends);
                })
                .catch(error => {
                    console.error('[StompClientWrapper] 刷新好友列表失败:', error);
                });
                
            // 刷新好友请求列表
            this.refreshFriendRequests()
                .then((updatedRequests) => {
                    console.log('[StompClientWrapper] 好友请求列表已刷新，新列表:', updatedRequests);
                })
                .catch(error => {
                    console.error('[StompClientWrapper] 刷新好友请求列表失败:', error);
                });
                
        } catch (e) {
            console.error('[StompClientWrapper] 处理好友接受消息出错:', e);
        }
    }
    
    // 新增：处理好友请求被拒绝的消息
    _handleFriendRequestRejected(message) {
        try {
            console.log('[StompClientWrapper] 收到好友请求被拒绝的通知:', message);
            const { senderId, receiverId } = message; // senderId 是被拒绝的人，receiverId 是拒绝的人

            if (!senderId || !receiverId) {
                console.error('[StompClientWrapper] friendRequestRejected 消息缺少 senderId 或 receiverId:', message);
                return;
            }

            // 更新本地好友请求列表
            // 找到自己发送的、且被对方拒绝的请求
            const requestIndex = this.friendRequests.value.findIndex(req =>
                req.firstUserId === senderId.toString() && // 自己是发送者
                req.secondUserId === receiverId.toString() && // 对方是接收者
                req.status === 'requested' // 或者 'sent' (根据displayStatus的逻辑)
            );

            if (requestIndex !== -1) {
                this.friendRequests.value[requestIndex].status = 'deleted'; // 与后端Friends.Status一致
                this.friendRequests.value[requestIndex].displayStatus = 'rejected';
                console.log('[StompClientWrapper] 更新本地好友请求状态为已拒绝:', this.friendRequests.value[requestIndex]);
                // 可以选择性地从列表中移除，或者保留但标记为已拒绝
                // this.friendRequests.value.splice(requestIndex, 1);
            } else {
                console.warn('[StompClientWrapper] 未能在本地找到对应的已发送好友请求来更新为已拒绝状态。');
            }

            // 调用API刷新好友列表和好友请求列表，以确保数据完全同步
            // 尽管本地更新了，但刷新可以获取最新的全面数据，并触发Vue的响应式更新
            this.refreshFriendRequests()
                .then(requests => {
                     console.log('[StompClientWrapper] 好友请求列表已因拒绝事件刷新，新列表:', requests);
                     // 可以在这里触发一个自定义事件，通知其他组件（如ContactsView）更新其视图
                     this._trigger('friendRequestsUpdated', this.friendRequests.value);
                })
                .catch(error => {
                    console.error('[StompClientWrapper] 因拒绝事件刷新好友请求列表失败:', error);
                });

            // 通知用户
            const notificationMessage = message.message || `您发送给 ${message.rejectedByUsername || ('用户 ' + receiverId)} 的好友请求已被拒绝。`;
            this._trigger('showSystemNotification', { 
                title: '好友请求被拒',
                message: notificationMessage, 
                type: 'warning' 
            });

        } catch (e) {
            console.error('[StompClientWrapper] 处理好友请求被拒绝消息出错:', e);
        }
    }
    
    // 新增：处理好友请求被发送者取消的消息 (接收者视角)
    _handleFriendRequestCancelledBySender(message) {
        try {
            console.log('[StompClientWrapper] 收到好友请求被发送者取消的通知:', message);
            const { cancellerId, originalReceiverId, cancellerUsername } = message;

            // 确保当前用户是这个已取消请求的原接收者
            if (originalReceiverId != this.currentUserId.value) {
                console.warn('[StompClientWrapper] 收到的取消通知与当前用户不匹配:', message, '当前用户ID:', this.currentUserId.value);
                return;
            }

            // 在本地好友请求列表中找到这条请求并更新或移除
            // 这条请求的 firstUserId 是 cancellerId，secondUserId 是 originalReceiverId (即当前用户)
            const requestIndex = this.friendRequests.value.findIndex(req =>
                req.firstUserId === cancellerId.toString() &&
                req.secondUserId === originalReceiverId.toString() && // 当前用户是接收者
                req.status === 'requested' // 应该是处于请求状态的
            );

            if (requestIndex !== -1) {
                // 直接修改本地缓存的请求对象状态，以便UI立即响应
                const requestToUpdate = this.friendRequests.value[requestIndex];
                requestToUpdate.status = 'deleted'; // 与后端将设置的状态一致
                requestToUpdate.displayStatus = 'rejected'; // _processFriendRequests 会将 'deleted' 映射为 'rejected'
                console.log('[StompClientWrapper] 本地更新了被发送者取消的好友请求状态:', requestToUpdate);

                // 可选: 如果Vue在某些情况下没有检测到对象内部属性的更改，可以强制替换数组中的对象
                // this.friendRequests.value.splice(requestIndex, 1, {...requestToUpdate});
                // 或者更简单地，如果 friendRequests.value 本身是 reactive 的，上述直接修改属性通常足够

                // 如果之前的 splice 行为是期望的（即从列表中移除），则可以保留 splice：
                // const cancelledRequest = this.friendRequests.value.splice(requestIndex, 1)[0];
                // console.log('[StompClientWrapper] 从本地移除了被发送者取消的好友请求:', cancelledRequest);
                // 但根据您的问题，似乎是状态未更新，而不是未移除。所以我们优先更新状态。

            } else {
                console.warn('[StompClientWrapper] 未能在本地找到对应的待处理好友请求以标记为已取消。可能已被处理或不存在。');
            }

            // 刷新好友请求列表以确保与后端完全同步，这会获取最新数据并重新处理
            this.refreshFriendRequests()
                .then(requests => {
                     console.log('[StompClientWrapper] 好友请求列表已因取消事件刷新，新列表:', requests);
                     this._trigger('friendRequestsUpdated', this.friendRequests.value);
                })
                .catch(error => {
                    console.error('[StompClientWrapper] 因取消事件刷新好友请求列表失败:', error);
                });

            // 通知用户
            const notificationMessage = message.message || `${cancellerUsername || ('用户 ' + cancellerId)} 取消了发给您的好友请求。`;
            this._trigger('showSystemNotification', { 
                title: '好友请求已取消', 
                message: notificationMessage, 
                type: 'info' 
            });

        } catch (e) {
            console.error('[StompClientWrapper] 处理好友请求被发送者取消消息出错:', e);
        }
    }
    
    // 刷新好友列表
    refreshFriends() {
        if (!this.isConnected.value || !this.stompClient.value?.connected) {
            console.warn('[StompClientWrapper] 未连接到WebSocket服务器，无法刷新好友列表');
            return Promise.reject(new Error('未连接到服务器'));
        }

        console.log('[StompClientWrapper] 开始刷新好友列表，当前用户ID:', this.currentUserId.value);
        return new Promise((resolve, reject) => {
            try {
                // 使用统一的认证工具获取JWT令牌
                const jwt = getAuthToken();
                if (!jwt) {
                    return reject(new Error('用户未登录'));
                }
                
                // 调用后端API获取最新的好友列表
                internalPost('/api/friends/getFriends',
                    { userId: this.currentUserId.value.toString() },
                    {
                        'Authorization': 'Bearer ' + jwt
                    },
                    (data) => {
                        if (!data) {
                            console.error('[StompClientWrapper] 从服务器收到空数据');
                            return reject(new Error('从服务器收到空数据'));
                        }
                        
                        console.log('[StompClientWrapper] 刷新好友列表成功:', data);
                        
                        try {
                            // 更新好友列表
                            this.friends.value = data || [];
                            resolve(this.friends.value);
                        } catch (e) {
                            console.error('[StompClientWrapper] 处理好友数据出错:', e);
                            reject(e);
                        }
                    },
                    (errorMsg) => {
                        console.error('[StompClientWrapper] 刷新好友列表失败:', errorMsg);
                        reject(new Error(errorMsg));
                    }
                );
            } catch (e) {
                console.error('[StompClientWrapper] refreshFriends方法异常:', e);
                reject(e);
            }
        });
    }
    
    // 刷新好友请求列表
    refreshFriendRequests() {
        if (!this.isConnected.value || !this.stompClient.value?.connected) {
            console.warn('[StompClientWrapper] 未连接到WebSocket服务器，无法刷新好友请求列表');
            return Promise.reject(new Error('未连接到服务器'));
        }

        return new Promise((resolve, reject) => {
            try {
                // 使用统一的认证工具获取JWT令牌
                const jwt = getAuthToken();
                if (!jwt) {
                    return reject(new Error('用户未登录'));
                }
                
                // 调用后端API获取最新的好友请求列表
                internalPost('/api/friends/getFriendRequests',
                    { userId: this.currentUserId.value.toString() },
                    {
                        'Authorization': 'Bearer ' + jwt
                    },
                    (data) => {
                        if (!data) {
                            console.error('[StompClientWrapper] 从服务器收到空数据');
                            return reject(new Error('从服务器收到空数据'));
                        }
                        
                        console.log('[StompClientWrapper] 刷新好友请求列表成功，原始数据:', data);
                        
                        try {
                            // 处理请求数据，区分收到的和发送的
                            const processedRequests = this._processFriendRequests(data);
                            
                            // 更新好友请求列表
                            this.friendRequests.value = processedRequests || [];
                            
                            console.log('[StompClientWrapper] 处理后的好友请求列表:', this.friendRequests.value);
                            
                            resolve(this.friendRequests.value);
                        } catch (e) {
                            console.error('[StompClientWrapper] 处理好友请求数据出错:', e);
                            reject(e);
                        }
                    },
                    (errorMsg) => {
                        console.error('[StompClientWrapper] 刷新好友请求列表失败:', errorMsg);
                        reject(new Error(errorMsg));
                    }
                );
            } catch (e) {
                console.error('[StompClientWrapper] refreshFriendRequests方法异常:', e);
                reject(e);
            }
        });
    }

    // 处理自己发送的好友请求
    _handleFriendRequestSent(message) {
        // 确保friendRequests已初始化
        if (!this.friendRequests.value) {
            this.friendRequests.value = [];
        }
        
        // 检查是否包含完整的FriendsResponse对象
        if (message.friendsResponse) {
            // 使用后端提供的FriendsResponse对象
            const friendRequest = message.friendsResponse;
            
            // 检查是否已存在相同的请求
            const exists = this.friendRequests.value.some(req => 
                req.firstUserId === friendRequest.firstUserId && 
                req.secondUserId === friendRequest.secondUserId
            );
            
            // 如果不存在，添加到列表
            if (!exists) {
                // 添加状态为"sent"，表示是自己发送的
                friendRequest.status = 'sent';
                this.friendRequests.value.push(friendRequest);
                console.log('[StompClientWrapper] 添加自己发送的好友请求:', friendRequest);
            }
        }
    }

    // 处理群组邀请
    _handleGroupInvite(message) {
        console.log('[StompClientWrapper] 收到群组邀请消息:', message);
        
        try {
            // 确保有群组邀请数据
            if (!message.id && !message.groupId) {
                console.warn('[StompClientWrapper] 群组邀请消息格式不正确，尝试其他字段:', message);
                // 尝试从其他字段获取信息
                if (!message.invitation && !message.invitationId) {
                    console.error('[StompClientWrapper] 群组邀请消息不包含必要信息，无法处理:', message);
                    return;
                }
            }
            
            // 检查消息状态，判断是新邀请还是邀请状态更新
            const status = (message.status?.toLowerCase?.() || '').trim();
            
            // 如果是邀请被接受的通知，触发群组列表刷新
            if (status === 'accepted' || status === 'joined') {
                console.log('[StompClientWrapper] 群组邀请已被接受，刷新群组列表');
                
                // 立即刷新群组列表
                this.refreshGroups()
                    .then(() => {
                        console.log('[StompClientWrapper] 因群组邀请接受，群组列表已刷新');
                        // 显示成功通知
                        const groupName = message.groupName || '群组';
                        this._trigger('showSystemNotification', {
                            title: '加入群组',
                            message: `您已成功加入群聊 ${groupName}`,
                            type: 'success'
                        });
                    })
                    .catch(error => {
                        console.error('[StompClientWrapper] 刷新群组列表失败:', error);
                    });
                
                // 同时刷新群组邀请列表
                this.refreshGroupInvitations()
                    .then(invitations => {
                        console.log('[StompClientWrapper] 群组邀请列表已刷新:', invitations);
                        this._trigger('onGroupInvitationsUpdated', invitations);
                    })
                    .catch(error => {
                        console.error('[StompClientWrapper] 刷新群组邀请列表失败:', error);
                    });
                
                return; // 处理完成，直接返回
            }
            
            // 标准化状态值处理逻辑（对于新邀请）
            let normalizedStatus = 'pending';
            if (status === '' || status === 'pending' || status === 'requested') {
                normalizedStatus = 'pending';
            } else if (status === 'accepted' || status === 'joined') {
                normalizedStatus = 'accepted';
            } else if (status === 'rejected' || status === 'declined' || status === 'refused' || status === 'denied') {
                normalizedStatus = 'rejected';
            }
            
            // 直接将消息添加到临时群组邀请列表中
            // 这样UI可以立即显示，不需要等待API刷新
            const tempInvitation = {
                id: message.id || message.invitationId || Date.now(),
                groupId: message.groupId,
                groupName: message.groupName || '未知群组',
                inviterId: message.inviterId,
                inviterName: message.inviterName || '未知用户',
                inviteeId: this.currentUserId.value,
                status: normalizedStatus,
                createdAt: message.createdAt || new Date().toISOString()
            };
            
            // 检查是否已存在相同邀请
            const exists = this.groupInvitations.value.some(inv => 
                inv.id === tempInvitation.id ||
                (inv.groupId === tempInvitation.groupId && inv.inviterId === tempInvitation.inviterId)
            );
            
            if (!exists) {
                // 添加到列表
                this.groupInvitations.value.push(tempInvitation);
                console.log('[StompClientWrapper] 临时添加群组邀请到列表:', tempInvitation);
            }
            
            // 刷新群组邀请列表
            this.refreshGroupInvitations()
                .then(invitations => {
                    console.log('[StompClientWrapper] 群组邀请列表已刷新:', invitations);
                    // 触发事件通知UI更新
                    this._trigger('onGroupInvitationsUpdated', invitations);
                })
                .catch(error => {
                    console.error('[StompClientWrapper] 刷新群组邀请列表失败:', error);
                    // 即使刷新失败，也触发事件，以便UI能显示临时添加的邀请
                    this._trigger('onGroupInvitationsUpdated', this.groupInvitations.value);
                });
            
            // 通知用户（仅对新邀请）
            if (normalizedStatus === 'pending') {
                const inviterName = message.inviterName || ('用户' + message.inviterId);
                const groupName = message.groupName || ('群组' + message.groupId);
                const notificationMessage = `${inviterName} 邀请您加入群聊 ${groupName}`;
                
                this._trigger('showSystemNotification', { 
                    title: '新群聊邀请', 
                    message: notificationMessage, 
                    type: 'info' 
                });
            }
        } catch (e) {
            console.error('[StompClientWrapper] 处理群组邀请消息出错:', e);
        }
    }
    
    // 刷新群组邀请列表
    refreshGroupInvitations() {
        if (!this.isConnected.value || !this.stompClient.value?.connected) {
            console.warn('[StompClientWrapper] 未连接到WebSocket服务器，无法刷新群组邀请列表');
            return Promise.reject(new Error('未连接到服务器'));
        }

        console.log('[StompClientWrapper] 开始刷新群组邀请列表');
        return new Promise((resolve, reject) => {
            try {
                // 使用groupApi中的方法获取群组邀请
                getReceivedGroupInvitations()
                    .then(invitations => {
                        console.log('[StompClientWrapper] 刷新群组邀请列表成功:', invitations);
                        // 更新群组邀请列表
                        this.groupInvitations.value = invitations || [];
                        resolve(this.groupInvitations.value);
                    })
                    .catch(error => {
                        console.error('[StompClientWrapper] 刷新群组邀请列表失败:', error);
                        reject(error);
                    });
            } catch (e) {
                console.error('[StompClientWrapper] refreshGroupInvitations方法异常:', e);
                reject(e);
            }
        });
    }

    // 处理群组管理通知
    _handleGroupNotification(message) {
        try {
            console.log('[StompClientWrapper] 处理群组通知:', message);
            
            // 检查是否是群组邀请（GroupInvitationResponse对象）
            if (message.id && message.groupId && message.inviterId && !message.type) {
                console.log('[StompClientWrapper] 识别为群组邀请通知，转发到群组邀请处理器');
                this._handleGroupInvite(message);
                return;
            }
            
            // 处理群组管理通知（包含type字段）
            switch (message.type) {
                case 'GROUP_MEMBER_REMOVED':
                    this._handleMemberRemovedNotification(message);
                    break;
                case 'GROUP_NAME_CHANGED':
                    this._handleGroupNameChangedNotification(message);
                    break;
                case 'GROUP_DISSOLVED':
                    this._handleGroupDissolvedNotification(message);
                    break;
                case 'groupInvite':
                    // 处理群组邀请相关通知（包括加入成功）
                    this._handleGroupInviteNotification(message);
                    break;
                case 'GROUP_EXIT_SUCCESS':
                    // 处理退出群组成功通知
                    this._handleGroupExitSuccessNotification(message);
                    break;
                default:
                    console.log('[StompClientWrapper] 未处理的群组通知类型:', message.type);
                    // 对于未知类型，仍然显示系统通知
                    if (message.message) {
                        this._trigger('showSystemNotification', {
                            title: '群组通知',
                            message: message.message,
                            type: 'info'
                        });
                    }
            }
        } catch (e) {
            console.error('[StompClientWrapper] 处理群组通知失败:', e);
        }
    }

    // 处理成员被移除通知
    _handleMemberRemovedNotification(message) {
        console.log('[StompClientWrapper] 处理成员被移除通知:', message);
        
        // 显示通知消息
        const notificationMessage = message.message || '您已被移出群组';
        this._trigger('showSystemNotification', {
            title: '群组通知',
            message: notificationMessage,
            type: 'warning'
        });
        
        // 刷新群组列表，因为用户可能被移出某个群组
        this.refreshGroups()
            .then(() => {
                console.log('[StompClientWrapper] 因成员被移除，群组列表已刷新');
            })
            .catch(error => {
                console.error('[StompClientWrapper] 刷新群组列表失败:', error);
            });
    }

    // 处理群名变更通知
    _handleGroupNameChangedNotification(message) {
        console.log('[StompClientWrapper] 处理群名变更通知:', message);
        
        // 显示通知消息
        const notificationMessage = message.message || `群组名称已更新`;
        this._trigger('showSystemNotification', {
            title: '群名变更',
            message: notificationMessage,
            type: 'info'
        });
        
        // 刷新群组列表以获取新的群名
        this.refreshGroups()
            .then(() => {
                console.log('[StompClientWrapper] 因群名变更，群组列表已刷新');
            })
            .catch(error => {
                console.error('[StompClientWrapper] 刷新群组列表失败:', error);
            });
    }

    // 处理群组解散通知
    _handleGroupDissolvedNotification(message) {
        console.log('[StompClientWrapper] 处理群组解散通知:', message);
        
        // 显示通知消息
        const notificationMessage = message.message || '群组已被解散';
        this._trigger('showSystemNotification', {
            title: '群组解散',
            message: notificationMessage,
            type: 'error'
        });
        
        // 刷新群组列表，因为群组已被解散
        this.refreshGroups()
            .then(() => {
                console.log('[StompClientWrapper] 因群组解散，群组列表已刷新');
            })
            .catch(error => {
                console.error('[StompClientWrapper] 刷新群组列表失败:', error);
            });
    }

    // 获取通知标题
    _getNotificationTitle(type) {
        const titleMap = {
            'GROUP_MEMBER_REMOVED': '群组通知',
            'GROUP_NAME_CHANGED': '群名变更',
            'GROUP_DISSOLVED': '群组解散',
            'GROUP_INVITE': '群组邀请'
        };
        return titleMap[type] || '系统通知';
    }

    // 获取通知类型
    _getNotificationType(type) {
        const typeMap = {
            'GROUP_MEMBER_REMOVED': 'warning',
            'GROUP_NAME_CHANGED': 'info',
            'GROUP_DISSOLVED': 'error',
            'GROUP_INVITE': 'info'
        };
        return typeMap[type] || 'info';
    }

    // 处理群组事件（从群组事件频道收到的）
    _handleGroupEvent(event) {
        console.log('[StompClientWrapper] 处理群组事件:', event);
        
        try {
            switch (event.type) {
                case 'MEMBER_REMOVED':
                    this._handleGroupMemberRemovedEvent(event);
                    break;
                case 'GROUP_NAME_CHANGED':
                    this._handleGroupNameChangedEvent(event);
                    break;
                case 'GROUP_DISSOLVED':
                    this._handleGroupDissolvedEvent(event);
                    break;
                default:
                    console.log('[StompClientWrapper] 未处理的群组事件类型:', event.type);
            }
        } catch (e) {
            console.error('[StompClientWrapper] 处理群组事件失败:', e);
        }
    }

    // 处理群组成员被移除事件
    _handleGroupMemberRemovedEvent(event) {
        console.log('[StompClientWrapper] 处理群组成员被移除事件:', event);
        
        // 刷新群组详情（如果当前正在查看该群组的详情）
        if (event.groupId) {
            // 触发群组详情更新事件
            this._trigger('onGroupMemberChanged', event);
        }
    }

    // 处理群组名称变更事件
    _handleGroupNameChangedEvent(event) {
        console.log('[StompClientWrapper] 处理群组名称变更事件:', event);
        
        // 更新本地群组列表中的群名
        if (event.groupId && event.newName) {
            const group = this.groups.value.find(g => g.groupId === event.groupId);
            if (group) {
                group.groupName = event.newName;
                console.log('[StompClientWrapper] 本地更新群组名称:', group);
            }
            
            // 触发群组信息更新事件
            this._trigger('onGroupInfoChanged', event);
        }
    }

    // 处理群组解散事件（其他成员收到的通知）
    _handleGroupDissolvedEvent(event) {
        console.log('[StompClientWrapper] 处理群组解散事件:', event);
        
        // 从本地群组列表中移除该群组
        if (event.groupId) {
            const index = this.groups.value.findIndex(g => g.groupId === event.groupId);
            if (index !== -1) {
                const removedGroup = this.groups.value.splice(index, 1)[0];
                console.log('[StompClientWrapper] 从本地移除解散的群组:', removedGroup);
            }
            
            // 触发群组解散事件
            this._trigger('onGroupDissolved', event);
        }
    }

    // --- 发送消息 ---
    sendPublicMessage(content, groupId, fileData = null) {
        // 增强连接状态检查
        if (!this.isConnected.value || !this.stompClient.value || !this.stompClient.value.connected) {
            const errorMsg = '未连接到WebSocket服务器，无法发送消息';
            console.warn('[StompClientWrapper] ' + errorMsg);
            this._trigger('onError', errorMsg);
            return false;
        }
        
        const destination = '/app/chat/channel'; // 发送到后端的 @MessageMapping
        try {
            // 使用新的临时消息ID生成器
            const tempId = this.generateTempId();
            
            // 构建消息载荷，支持文件消息
            const messagePayload = { 
                content: content, 
                groupId: groupId,
                tempId: tempId
            };
            
            // 如果有文件数据，添加到消息中
            if (fileData) {
                Object.assign(messagePayload, {
                    fileUrl: fileData.fileUrl,
                    fileName: fileData.fileName,
                    fileType: fileData.fileType,
                    fileSize: fileData.fileSize,
                    messageType: fileData.messageType || 'FILE'
                });
            }
            
            this.stompClient.value.publish({ 
                destination: destination, // 发送消息的目的地
                body: JSON.stringify(messagePayload), // 发送内容和群组id
                headers: {
                    'user-id': this.currentUserId.value,
                    'group-id': groupId
                }
            });
            console.log(`[StompClientWrapper] Sent public message to ${destination}:`, messagePayload);
            
            return tempId; // 返回临时ID，以便前端跟踪
        } catch (e) {
            console.error(`[StompClientWrapper] Failed to send public message to ${destination}:`, e);
            this._trigger('onError', `发送群组消息失败: ${e.message}`);
            return false;
        }
    }

    sendPrivateMessage(toUserId, content, toUserName, fileData = null) {
        // 增强连接状态检查
        if (!this.isConnected.value || !this.stompClient.value || !this.stompClient.value.connected) {
            const errorMsg = '未连接到WebSocket服务器，无法发送消息';
            console.warn('[StompClientWrapper] ' + errorMsg);
            this._trigger('onError', errorMsg);
            return false;
        }
        
        if (!toUserId || !content) {
            const errorMsg = '发送私人消息需要提供接收者ID和内容';
            console.warn('[StompClientWrapper] ' + errorMsg);
            this._trigger('onError', errorMsg);
            return false;
        }
        
        const destination = '/app/chat/private'; 
        try {
            // 使用新的临时消息ID生成器
            const tempId = this.generateTempId();
            
            // 构建消息载荷，支持文件消息
            const messagePayload = {
                receiverId: toUserId,   // Use receiverId to match backend getter
                content: content,       // Content is essential
                tempId: tempId
            };
            
            // 如果有文件数据，添加到消息中
            if (fileData) {
                Object.assign(messagePayload, {
                    fileUrl: fileData.fileUrl,
                    fileName: fileData.fileName,
                    fileType: fileData.fileType,
                    fileSize: fileData.fileSize,
                    messageType: fileData.messageType || 'FILE'
                });
            }
            
            console.log('[StompClientWrapper] 准备发送私人消息 payload:', messagePayload);
            
            this.stompClient.value.publish({ 
                destination: destination,
                body: JSON.stringify(messagePayload),
                // Headers might still be useful for context or middleware, but payload is key
                headers: {
                    'user-id': this.currentUserId.value, // Redundant if principal is used backend
                    'from-user': this.currentUser.value, // Redundant if principal is used backend
                    // 'to-user-id': toUserId // Can be kept if useful for backend logging/routing logic
                }
            });
            console.log(`[StompClientWrapper] 已发送私人消息到 ${destination}，接收者ID: ${toUserId}`);
            
            return tempId;
        } catch (e) {
            console.error(`[StompClientWrapper] 发送私人消息失败:`, e);
            this._trigger('onError', `发送私人消息失败: ${e.message}`);
            return false;
        }
    }

    // 处理群组邀请相关通知（包括加入成功）
    _handleGroupInviteNotification(message) {
        console.log('[StompClientWrapper] 处理群组邀请通知:', message);
        
        try {
            // 检查是否是加入成功的通知
            if (message.status === 'accepted' && message.inviteeId === this.currentUserId.value) {
                console.log('[StompClientWrapper] 收到群组加入成功通知');
                
                // 显示成功通知
                const groupName = message.groupName || '群组';
                this._trigger('showSystemNotification', {
                    title: '加入群组',
                    message: `您已成功加入群聊 ${groupName}`,
                    type: 'success'
                });
                
                // 刷新群组列表
                this.refreshGroups()
                    .then(() => {
                        console.log('[StompClientWrapper] 因群组加入成功，群组列表已刷新');
                    })
                    .catch(error => {
                        console.error('[StompClientWrapper] 刷新群组列表失败:', error);
                    });
                
                // 刷新群组邀请列表
                this.refreshGroupInvitations()
                    .then(invitations => {
                        console.log('[StompClientWrapper] 群组邀请列表已刷新:', invitations);
                        this._trigger('onGroupInvitationsUpdated', invitations);
                    })
                    .catch(error => {
                        console.error('[StompClientWrapper] 刷新群组邀请列表失败:', error);
                    });
            } else {
                // 其他群组邀请通知，转发到群组邀请处理器
                this._handleGroupInvite(message);
            }
        } catch (e) {
            console.error('[StompClientWrapper] 处理群组邀请通知失败:', e);
        }
    }

    // 处理退出群组成功通知
    _handleGroupExitSuccessNotification(message) {
        console.log('[StompClientWrapper] 处理退出群组成功通知:', message);
        
        try {
            // 显示退出成功通知
            const groupName = message.groupName || '群组';
            this._trigger('showSystemNotification', {
                title: '退出群组',
                message: `您已成功退出群聊 ${groupName}`,
                type: 'success'
            });
            
            // 刷新群组列表
            this.refreshGroups()
                .then(() => {
                    console.log('[StompClientWrapper] 因退出群组成功，群组列表已刷新');
                })
                .catch(error => {
                    console.error('[StompClientWrapper] 刷新群组列表失败:', error);
                });
        } catch (e) {
            console.error('[StompClientWrapper] 处理退出群组成功通知失败:', e);
        }
    }
}

// 单例模式导出，或者根据需要导出类本身
const stompClientInstance = new StompClientWrapper();

// 添加全局引用以便在API函数中使用
if (typeof window !== 'undefined') {
    window.stompClientInstance = stompClientInstance;
}

export default stompClientInstance; // 导出单例
// export { StompClientWrapper }; // 或者导出类