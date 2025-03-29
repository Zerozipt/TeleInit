// the-front/src/net/websocket.js
import SockJS from 'sockjs-client/dist/sockjs.min.js'; // 确保路径正确
import { Stomp } from '@stomp/stompjs';
import { ref, shallowRef } from 'vue'; // 使用 shallowRef 优化 StompClient 实例

const SOCKET_URL = 'http://localhost:8080/ws-chat'; // 后端 WebSocket 端点

class StompClientWrapper {
    constructor() {
        this.stompClient = shallowRef(null); // 使用 shallowRef
        this.isConnected = ref(false);
        this.currentUser = ref('');
        this.connectionPromise = null;
        this.resolveConnectionPromise = null;
        this.rejectConnectionPromise = null;
        this.subscriptions = {}; // 存储订阅，键为 destination，值为 STOMP subscription 对象

        // 回调注册表
        this.callbacks = {
            onConnected: [],
            onDisconnected: [],
            onError: [],
            onPublicMessage: [],
            onPrivateMessage: [],
        };
    }

    _resetConnectionPromise() {
        this.connectionPromise = new Promise((resolve, reject) => {
            this.resolveConnectionPromise = resolve;
            this.rejectConnectionPromise = reject;
        });
    }

    // --- 事件注册 ---
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
          console.warn('[StompClientWrapper] Already connected or connecting.');  
          return this.connectionPromise || Promise.resolve();  
      }  
  
      this._resetConnectionPromise();  
  
      try {  
          if (!jwt) {  
              const errorMsg = 'JWT token is required for connection.';  
              this._trigger('onError', errorMsg);  
              this.rejectConnectionPromise(new Error(errorMsg));  
              return this.connectionPromise;  
          }  
  
          // 将JWT添加为URL参数  
          const socket = new SockJS(`${SOCKET_URL}?token=${jwt}`);  
          this.stompClient.value = Stomp.over(socket);  
  
          // 禁用 STOMP 调试信息  
          this.stompClient.value.debug = (str) => { /* console.log('[STOMP Debug]', str); */ };  
  
          // STOMP连接不需要再传递JWT，因为已在URL中  
          this.stompClient.value.connect(  
              {}, // 空headers，JWT已在URL中  
              (frame) => {  
                  // 连接成功回调 - 保持不变  
                  this.isConnected.value = true;  
                  this.currentUser.value = frame.headers['user-name'] || 'Unknown User';  
                  console.log('[StompClientWrapper] Connected. User:', this.currentUser.value);  
                  this._trigger('onConnected', this.currentUser.value);  
  
                  this._subscribeToPublic();  
                  this._subscribeToPrivate();  
  
                  this.resolveConnectionPromise();  
              },  
              (error) => {  
                  // 错误回调 - 保持不变  
                  console.error('[StompClientWrapper] Connection error:', error);  
                  const errorMessage = error.headers?.message || 'Connection failed';  
                  this.isConnected.value = false;  
                  this.currentUser.value = '';  
                  this._trigger('onError', errorMessage);  
                  this.rejectConnectionPromise(error);  
                  this._cleanup();  
              }  
          );  
      } catch (err) {  
          console.error('[StompClientWrapper] Connection setup exception:', err);  
          this.isConnected.value = false;  
          this._trigger('onError', `Connection exception: ${err.message}`);  
          this.rejectConnectionPromise(err);  
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

    _cleanup() {
        this.stompClient.value = null;
        this.isConnected.value = false;
        this.currentUser.value = '';
        this.subscriptions = {};
         // 重置 Promise 状态，但不创建新的 Promise
        this.connectionPromise = null;
        this.resolveConnectionPromise = null;
        this.rejectConnectionPromise = null;
    }

    // --- 订阅 ---
    _subscribeToPublic() {
        const destination = '/topic/public/general';
        if (!this.subscriptions[destination] && this.stompClient.value?.connected) {
            try {
                 this.subscriptions[destination] = this.stompClient.value.subscribe(destination, (message) => {
                    try {
                        const parsedMessage = JSON.parse(message.body);
                        // 使用 ISO 格式或根据需要格式化时间戳
                        // parsedMessage.timestamp = new Date(parsedMessage.timestamp).toLocaleString();
                        console.log('[StompClientWrapper] Received public message:', parsedMessage);
                        this._trigger('onPublicMessage', parsedMessage);
                    } catch (e) {
                        console.error('[StompClientWrapper] Error parsing public message:', e, message.body);
                    }
                });
                console.log(`[StompClientWrapper] Subscribed to ${destination}`);
            } catch(e) {
                console.error(`[StompClientWrapper] Failed to subscribe to ${destination}:`, e);
                 this._trigger('onError', `Failed to subscribe to public channel: ${e.message}`);
            }

        }
    }

    _subscribeToPrivate() {
        const destination = '/user/queue/private'; // 用户需要订阅这个地址来接收私信
        if (!this.subscriptions[destination] && this.stompClient.value?.connected) {
             try {
                this.subscriptions[destination] = this.stompClient.value.subscribe(destination, (message) => {
                    try {
                        const parsedMessage = JSON.parse(message.body);
                        // parsedMessage.timestamp = new Date(parsedMessage.timestamp).toLocaleString();
                        console.log('[StompClientWrapper] Received private message:', parsedMessage);
                        this._trigger('onPrivateMessage', parsedMessage);
                    } catch (e) {
                        console.error('[StompClientWrapper] Error parsing private message:', e, message.body);
                    }
                });
                 console.log(`[StompClientWrapper] Subscribed to ${destination}`);
            } catch(e) {
                console.error(`[StompClientWrapper] Failed to subscribe to ${destination}:`, e);
                this._trigger('onError', `Failed to subscribe to private channel: ${e.message}`);
            }
        }
    }

    // --- 发送消息 ---
    sendPublicMessage(content) {
        if (!this.isConnected.value) {
            console.warn('[StompClientWrapper] Cannot send message, not connected.');
            return;
        }
        const destination = '/app/chat/public'; // 发送到后端的 @MessageMapping
        try {
            this.stompClient.value.publish({
                destination: destination,
                body: JSON.stringify({ content: content }), // 只发送内容
            });
             console.log(`[StompClientWrapper] Sent public message to ${destination}:`, { content });
        } catch (e) {
            console.error(`[StompClientWrapper] Failed to send public message to ${destination}:`, e);
            this._trigger('onError', `Failed to send public message: ${e.message}`);
        }
    }

    sendPrivateMessage(toUser, content) {
        if (!this.isConnected.value) {
            console.warn('[StompClientWrapper] Cannot send message, not connected.');
            return;
        }
        if (!toUser || !content) {
             console.warn('[StompClientWrapper] Target user and content are required for private message.');
            return;
        }
        const destination = '/app/chat/private'; // 发送到后端的 @MessageMapping
        try {
            this.stompClient.value.publish({
                destination: destination,
                body: JSON.stringify({ toUser: toUser, content: content }), // 发送接收者和内容
            });
            console.log(`[StompClientWrapper] Sent private message to ${destination}:`, { toUser, content });
        } catch (e) {
             console.error(`[StompClientWrapper] Failed to send private message to ${destination}:`, e);
             this._trigger('onError', `Failed to send private message: ${e.message}`);
        }
    }
}

// 单例模式导出，或者根据需要导出类本身
const stompClientInstance = new StompClientWrapper();
export default stompClientInstance; // 导出单例
// export { StompClientWrapper }; // 或者导出类