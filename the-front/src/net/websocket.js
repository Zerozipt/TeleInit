// the-front/src/net/websocket.js
import SockJS from 'sockjs-client/dist/sockjs.min.js'; // 确保路径正确
import { Stomp } from '@stomp/stompjs';
import { ref, shallowRef } from 'vue'; // 使用 shallowRef 优化 StompClient 实例
import axios from 'axios'; // 导入axios用于HTTP请求

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
        this.heartbeatInterval = 10000; // 心跳间隔,单位为毫秒
        this.heartbeatTimer = null; // 心跳定时器
        this.reconnectInterval = 5000; // 重连间隔,单位为毫秒
        this.friends = ref([]);
        this.friendRequests = ref([]);
        this.groups = ref([]);
        //群组消息的结构是二维数组，数组中每一项是一个Group_message对象的数组，代表一个群聊的聊天记录
        //对于这样的结构，为了方便前端显示，可以使用一个map来存储，key为群组id，value为群组消息的数组
        this.groupMessages = ref(new Map());
        //私聊消息的结构是一维数组，数组中存储的是PrivateChatMessage对象
        this.privateMessages = ref([]); 
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
            // 网络断开时，触发清理但不主动断开连接
            // Stomp客户端会自行处理网络中断
            this._trigger('onError', '网络连接已断开');
        });
        
        window.addEventListener('online', () => {
            console.log('[StompClientWrapper] 网络连接恢复');
            // 网络恢复时，检查连接状态
            if (!this.isConnected.value && !this.stompClient.value?.connected) {
                console.log('[StompClientWrapper] 尝试重新连接');
                // 可以尝试重新连接，或者通知用户手动重连
                this._trigger('onError', '网络已恢复，请刷新页面重新连接');
            }
        });
        }
    }

    // 刷新群组列表
    refreshGroups() {
        if (!this.isConnected.value || !this.stompClient.value?.connected) {
            console.warn('[StompClientWrapper] 未连接到WebSocket服务器，无法刷新群组列表');
            return Promise.reject(new Error('未连接到服务器'));
        }

        return new Promise((resolve, reject) => {
            try {
                // 获取JWT令牌
                const authData = localStorage.getItem('authorize');
                if (!authData) {
                    return reject(new Error('用户未登录'));
                }
                
                const parsedAuth = JSON.parse(authData);
                const jwt = parsedAuth?.token;
                
                if (!jwt) {
                    return reject(new Error('无效的认证信息'));
                }
                
                // 调用后端API获取最新的群组列表
                internalPost('/api/chat/GetGroups',
                    null,
                    {
                        'Authorization': 'Bearer ' + jwt
                    },
                    (data) => {
                        if (!data) {
                            console.error('[StompClientWrapper] 从服务器收到空数据');
                            return reject(new Error('从服务器收到空数据'));
                        }
                        
                        console.log('[StompClientWrapper] 刷新群组列表成功:', data);
                        
                        try {
                            // 更新群组列表
                            this.groups.value = data || [];
                            
                            // 更新群组订阅
                            this._subscribeToPublic();
                            
                            resolve(this.groups.value);
                        } catch (e) {
                            console.error('[StompClientWrapper] 处理群组数据出错:', e);
                            reject(e);
                        }
                    },
                    (errorMsg) => {
                        console.error('[StompClientWrapper] 刷新群组列表失败:', errorMsg);
                        reject(new Error(errorMsg));
                    }
                );
            } catch (e) {
                console.error('[StompClientWrapper] refreshGroups方法异常:', e);
                reject(e);
            }
        });
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
                                const parsedFriendRequests = JSON.parse(data.friendRequests) || [];
                                this.friendRequests.value = parsedFriendRequests;
                                console.log('[StompClientWrapper] 解析好友请求列表成功, 好友请求列表:', parsedFriendRequests);
                                console.log('[StompClientWrapper] 解析好友请求列表成功, 好友请求数量:', parsedFriendRequests.length);
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
                heartbeatIncoming: 5000,
                heartbeatOutgoing: 5000,
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
                    'heart-beat': '5000,5000'
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
        //由于用户可能有很多群组，所以需要批量订阅
        this.groups.value.forEach(groupId => {
            const destination = '/topic/channel/' + groupId.groupId;
            console.log('[StompClientWrapper] 正在订阅群组消息频道:', destination);
            // 如果订阅不存在，并且连接成功，则订阅
            if (!this.subscriptions[destination] && this.stompClient.value?.connected) {
                try {
                // 订阅
                console.log('[StompClientWrapper] 正在订阅群组消息频道:', destination);
                 this.subscriptions[destination] = this.stompClient.value.subscribe(destination, (message) => {
                    try {
                        // 不再需要从 header 读取 groupId
                        // const messageGroupId = message.headers['groupId'];
                        // if (messageGroupId === groupId) { // groupId 在这里是 Group_member 对象
                        const parsedMessage = JSON.parse(message.body); // message.body 现在是 ChatMessage 的 JSON 字符串
                        // 验证收到的消息中的 groupId 是否与当前订阅的群组匹配
                        if (parsedMessage.groupId === groupId.groupId) { // 使用 groupId 对象的 groupId 属性
                            // 使用 ISO 格式或根据需要格式化时间戳
                            // parsedMessage.timestamp = new Date(parsedMessage.timestamp).toLocaleString();
                            console.log('[StompClientWrapper] Received public message for correct group:', parsedMessage);
                            // 触发事件，通知前端收到消息
                            this._trigger('onPublicMessage', parsedMessage);
                        } else {
                            console.warn('[StompClientWrapper] Received public message for wrong group:', parsedMessage, 'Expected group:', groupId.groupId);
                        }
                        // }
                    } catch (e) {
                        console.error('[StompClientWrapper] Error parsing public message:', e, message.body);
                    }
                });
                console.log(`[StompClientWrapper] Subscribed to ${destination}`);
            } catch(e) {
                console.error(`[StompClientWrapper] Failed to subscribe to ${destination}:`, e);
                 this._trigger('onError', `Failed to subscribe to public channel: ${e.message}`);
            }
        }});
    }
    
    _subscribeOnlineStatus() {
        const onlineDest = '/user/queue/online';
        if (!this.subscriptions[onlineDest] && this.stompClient.value?.connected) {
            this.subscriptions[onlineDest] = this.stompClient.value.subscribe(onlineDest, (message) => {
                const statusUpdate = JSON.parse(message.body);
                console.log('[StompClientWrapper] User Online:', statusUpdate);
                // 在这里触发一个特定的 'onUserOnline' 事件
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
                 // 在这里触发一个特定的 'onUserOffline' 事件
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
        const destination = '/user/queue/system'; // 修改为用户专属的系统消息队列
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
        }
    }

    // 处理好友请求
    _handleFriendRequest(message) {
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
                status: 'requested'
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

    // --- 发送消息 ---
    sendPublicMessage(content, groupId) {
        // 增强连接状态检查
        if (!this.isConnected.value || !this.stompClient.value || !this.stompClient.value.connected) {
            const errorMsg = '未连接到WebSocket服务器，无法发送消息';
            console.warn('[StompClientWrapper] ' + errorMsg);
            this._trigger('onError', errorMsg);
            return false;
        }
        
        const destination = '/app/chat/channel'; // 发送到后端的 @MessageMapping
        try {
            this.stompClient.value.publish({ 
                destination: destination, // 发送消息的目的地
                body: JSON.stringify({ content: content, groupId: groupId }), // 发送内容和群组id
                headers: {
                    'user-id': this.currentUserId.value,
                    'group-id': groupId
                }
            });
            console.log(`[StompClientWrapper] Sent public message to ${destination}:`, { content, groupId });
            return true;
        } catch (e) {
            console.error(`[StompClientWrapper] Failed to send public message to ${destination}:`, e);
            this._trigger('onError', `发送群组消息失败: ${e.message}`);
            return false;
        }
    }

    sendPrivateMessage(toUserId, content, toUserName) {
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
            // Corrected payload to match backend expectation (receiverId and content)
            const messagePayload = {
                receiverId: toUserId,   // Use receiverId to match backend getter
                content: content           // Content is essential
                // fromUserId, fromUser, toUser are likely set by backend or irrelevant in payload
            };
            
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
            return true;
        } catch (e) {
            console.error(`[StompClientWrapper] 发送私人消息失败:`, e);
            this._trigger('onError', `发送私人消息失败: ${e.message}`);
            return false;
        }
    }
}

// 单例模式导出，或者根据需要导出类本身
const stompClientInstance = new StompClientWrapper();
export default stompClientInstance; // 导出单例
// export { StompClientWrapper }; // 或者导出类