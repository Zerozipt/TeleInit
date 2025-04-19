import axios from 'axios';
import { ElMessage } from 'element-plus';

// 获取存储在 localStorage 中的 JWT Token
const getAuthToken = () => {
    try {
        const authData = localStorage.getItem('authorize');
        if (authData) {
            const parsedAuth = JSON.parse(authData);
            return parsedAuth?.token || null;
        }
    } catch (e) {
        console.error("Error reading auth token from localStorage:", e);
    }
    return null;
};

// 创建 Axios 实例
const apiClient = axios.create({
    baseURL: 'http://localhost:8080/api',
    timeout: 5000,
});

// 添加请求拦截器，自动附加 Authorization Header
apiClient.interceptors.request.use(
    (config) => {
        const token = getAuthToken();
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
export const getPrivateChatHistory = async (user1, user2, limit = 50) => {
    try {
        console.log(`尝试获取私聊历史: /chat/history/private, 参数: user1=${user1}, user2=${user2}, limit=${limit}`);
        const response = await apiClient.get(`/chat/history/private`, {
            params: { user1, user2, limit }
        });
        if (response.data.code === 200) {
            return response.data.data || [];
        } else {
            throw new Error(response.data.message || '获取私聊记录失败');
        }
    } catch (error) {
        console.error("获取私聊记录失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '获取私聊记录失败';
        throw new Error(errorMsg);
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