import axios from 'axios';
import { ElMessage } from 'element-plus';
import { getAuthToken } from '@/utils/auth';

// 使用统一的认证工具获取JWT Token
const getAuthTokenFromStorage = () => {
    return getAuthToken();
};

// 创建 Axios 实例
const apiClient = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 5000,
});
//规定，所有请求发送时，必须携带Authorization Header
// 添加请求拦截器，自动附加 Authorization Header
apiClient.interceptors.request.use(
    (config) => {
        const token = getAuthTokenFromStorage();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

/**
 * 获取私聊消息历史
 * @param {string} user1 第一个用户名
 * @param {string} user2 第二个用户名
 * @param {number} limit 最大消息数量
 * @returns {Promise<Array>} 消息列表
 */
export const getPrivateChatHistory = async (user1, user2, limit = 50, oldestMessageId) => {
    try {
        const userId = String(user1);
        const friendId = String(user2);
        console.log(`尝试获取私聊历史: /chat/history/private, 参数: user1=${userId}, user2=${friendId}, limit=${limit}, oldestMessageId=${oldestMessageId}`);
        
        const response = await apiClient.get(`/chat/history/private`, {
            params: { userId, friendId, limit, oldestMessageId }
        });
        
        console.log(`获取私聊历史响应:`, response.data);
        
        let privateMessages = [];
        if (response.data.data) {
            if (typeof response.data.data === 'string') {
                try {
                    const jsonObject = JSON.parse(response.data.data);
                    privateMessages = jsonObject.privateMessages || [];
                } catch (e) {
                    console.error("解析私聊历史JSON失败:", e);
                }
            } else {
                privateMessages = response.data.data.privateMessages || [];
            }
        }
        
        return privateMessages;
    } catch (error) {
        // 如果是400错误，表示没有更多历史记录
        if (error.response && error.response.status === 400) {
            console.log("没有更多历史记录");
            return []; // 返回空数组而不是抛出错误
        }
        
        console.error("获取私聊记录失败:", error);
        throw error; // 其他错误仍然抛出
    }
};

/**
 * 获取群聊消息历史
 * @param {string} groupId 群组ID
 * @param {number} limit 最大消息数量
 * @returns {Promise<Array>} 消息列表
 */
export const getGroupChatHistory = async (groupId, limit = 50) => {
    try {
        const response = await apiClient.get(`/chat/history/group`, {
            params: { groupId, limit }
        });
        if (response.data.code === 200) {
            return response.data.data || [];
        } else {
            throw new Error(response.data.message || '获取群聊记录失败');
        }
    } catch (error) {
        console.error("获取群聊记录失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '获取群聊记录失败';
        throw new Error(errorMsg);
    }
}; 