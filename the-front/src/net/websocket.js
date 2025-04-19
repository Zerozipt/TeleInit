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
        this.heartbeatInterval = 5000; // 心跳间隔,单位为毫秒
        this.heartbeatTimer = null; // 心跳定时器
        this.reconnectInterval = 5000; // 重连间隔,单位为毫秒
        this.friends = ref([]);
        this.groups = ref([]);
        // 回调注册表
        this.callbacks = {
            onConnected: [], // 存储回调函数，意为这个变量是一个数组，数组中存储的是回调函数
            onDisconnected: [],
            onError: [],
            onPublicMessage: [],
            onPrivateMessage: [],
            onFriendRequest: [], // 好友请求事件
        };
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
                                console.log('[StompClientWrapper] 解析群组列表成功, 群组数量:', parsedGroups.length);
                            }
                        } catch (parseError) {
                            console.error('[StompClientWrapper] 解析好友或群组数据出错:', parseError);
                        }
                        
                        if (this.resolveConnectionPromise) {
                            this.resolveConnectionPromise();
                        }
                        
                        this._subscribeToPublic();
                        this._subscribeToPrivate();
                        
                        this.isConnected.value = true;
                        console.log('[StompClientWrapper] 连接状态已更新:', this.isConnected.value);
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
    //eventName是事件名，callback是事件回调函数
    on(eventName, callback) {
        if (this.callbacks[eventName]) {
            this.callbacks[eventName].push(callback);
        } else {
            console.warn(`[StompClientWrapper] Unknown event name: ${eventName}`);
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
            // 取消所有订阅
            Object.values(this.subscriptions).forEach(sub => sub?.unsubscribe());
            this.subscriptions = {};

            this.stompClient.value.disconnect(() => {
                console.log('[StompClientWrapper] Disconnected.');
                this._cleanup();
                this._trigger('onDisconnected');
            });
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
    }

    // --- 订阅 ---
    _subscribeToPublic() {
        //订阅的频道是动态的，根据频道id订阅对应的频道,频道id是群组id,群组id是群组id的hash值对100取余
        //由于用户可能有很多群组，所以需要批量订阅
        this.groups.value.forEach(groupId => {
            const destination = '/topic/channel' + groupId % 100;
            // 如果订阅不存在，并且连接成功，则订阅
            if (!this.subscriptions[destination] && this.stompClient.value?.connected) {
                try {
                // 订阅
                 this.subscriptions[destination] = this.stompClient.value.subscribe(destination, (message) => {
                    try {
                        // 解析消息，主要要注意，发送来的消息头是groupId，要注意这个groupId是不是当前用户的群组
                        const messageGroupId = message.headers['group-id'];
                        if (messageGroupId === groupId) {
                            const parsedMessage = JSON.parse(message.body);
                            // 使用 ISO 格式或根据需要格式化时间戳
                            // parsedMessage.timestamp = new Date(parsedMessage.timestamp).toLocaleString();
                            console.log('[StompClientWrapper] Received public message:', parsedMessage);
                            // 触发事件，通知前端收到消息
                            this._trigger('onPublicMessage', parsedMessage);
                        }
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
    
    _subscribeToPrivate() { 
        if (!this.stompClient.value || !this.isConnected.value || !this.currentUserId.value) {
            console.warn('[StompClientWrapper] 无法订阅私人消息：客户端未连接或缺少用户ID');
            return;
        }

        try {
            // 订阅自己的私人消息通道
            const privateDestination = `/user/${this.currentUserId.value}/queue/messages`;
            
            if (this.subscriptions[privateDestination]) {
                console.log('[StompClientWrapper] 已存在对私人消息的订阅，跳过重复订阅');
                return;
            }
            
            console.log('[StompClientWrapper] 正在订阅私人消息通道:', privateDestination);
            
            this.subscriptions[privateDestination] = this.stompClient.value.subscribe(
                privateDestination,
                (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        console.log('[StompClientWrapper] 收到私人消息:', data);
                        this._trigger('onPrivateMessage', data);
                    } catch (e) {
                        console.error('[StompClientWrapper] 解析私人消息失败:', e, message.body);
                    }
                },
                { id: `private-sub-${this.currentUserId.value}` }
            );
            
            console.log('[StompClientWrapper] 私人消息通道订阅成功');
            
            // 订阅好友请求通道
            const friendRequestDestination = `/user/${this.currentUserId.value}/queue/friend-requests`;
            
            if (this.subscriptions[friendRequestDestination]) {
                console.log('[StompClientWrapper] 已存在对好友请求的订阅，跳过重复订阅');
                return;
            }
            
            console.log('[StompClientWrapper] 正在订阅好友请求通道:', friendRequestDestination);
            
            this.subscriptions[friendRequestDestination] = this.stompClient.value.subscribe(
                friendRequestDestination,
                (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        console.log('[StompClientWrapper] 收到好友请求:', data);
                        this._trigger('onFriendRequest', data);
                    } catch (e) {
                        console.error('[StompClientWrapper] 解析好友请求失败:', e, message.body);
                    }
                },
                { id: `friend-request-sub-${this.currentUserId.value}` }
            );
            
            console.log('[StompClientWrapper] 好友请求通道订阅成功');
            
        } catch (error) {
            console.error('[StompClientWrapper] 订阅私人消息失败:', error);
            this._trigger('onError', `订阅私人消息失败: ${error.message}`);
        }
    }

    // --- 发送消息 ---
    sendPublicMessage(content, groupId) {
        if (!this.isConnected.value) { // 如果未连接，则不发送消息
            console.warn('[StompClientWrapper] Cannot send message, not connected.');
            return;
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
        } catch (e) {
            console.error(`[StompClientWrapper] Failed to send public message to ${destination}:`, e);
            this._trigger('onError', `Failed to send public message: ${e.message}`);
        }
    }

    sendPrivateMessage(toUser, content,toUserName) {
        if (!this.isConnected.value) {
            console.warn('[StompClientWrapper] 未连接到服务器，无法发送消息');
            return;
        }
        if (!toUser || !content) {
            console.warn('[StompClientWrapper] 发送私人消息需要提供接收者和内容');
            return;
        }
        const destination = '/app/chat/private'; // 发送到后端的 @MessageMapping
        try {
            // 确保消息格式与后端PrivateChatMessage类匹配
            const message = {
                toUserId: toUser,          // 接收者ID，这是必需的
                content: content,          // 消息内容，这是必需的
                fromUserId: this.currentUserId.value,  // 这个在后端会被覆盖，但前端先设置
                fromUser: this.currentUser.value,       // 这个在后端会被覆盖，但前端先设置
                toUser: toUserName
            };
            
            console.log('[StompClientWrapper] 准备发送私人消息:', message);
            
            this.stompClient.value.publish({ 
                destination: destination,
                body: JSON.stringify(message),
                headers: {
                    'user-id': this.currentUserId.value,
                    'from-user': this.currentUser.value,
                    'to-user': toUser  // 在消息头中也设置接收者ID
                }
            });
            console.log(`[StompClientWrapper] 已发送私人消息到 ${destination}，接收者ID: ${toUser}`);
        } catch (e) {
            console.error(`[StompClientWrapper] 发送私人消息失败:`, e);
            this._trigger('onError', `发送私人消息失败: ${e.message}`);
        }
    }
}

// 单例模式导出，或者根据需要导出类本身
const stompClientInstance = new StompClientWrapper();
export default stompClientInstance; // 导出单例
// export { StompClientWrapper }; // 或者导出类