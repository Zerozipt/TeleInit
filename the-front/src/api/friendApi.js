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
 * 搜索用户
 * @param {string} searchTerm 搜索关键词（用户名）
 * @returns {Promise<Array>} 用户列表
 */
export const searchUsers = async (searchTerm) => {
    try {
        const response = await apiClient.get(`/users/search?term=${encodeURIComponent(searchTerm)}`);
        if (response.data.code === 200) {
            return response.data.data || []; // 提取RestBean中的data字段，确保返回数组
        } else {
            throw new Error(response.data.message || '搜索用户失败');
        }
    } catch (error) {
        console.error("搜索用户失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '搜索用户失败';
        throw new Error(errorMsg);
    }
};

/**
 * 发送好友请求
 * @param {number} targetUserId 要添加的好友ID
 * @returns {Promise<object>} 包含操作结果的响应
 */
export const addFriend = async (targetUserId) => {
    if (!targetUserId) {
        throw new Error('目标用户ID不能为空');
    }
    try {
        const response = await apiClient.post('/friends/request', { targetUserId });
        if (response.data.code === 200) {
            return response.data.data;
        } else {
            throw new Error(response.data.message || '添加好友失败');
        }
    } catch (error) {
        console.error("添加好友 API 请求失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '添加好友时发生错误';
        throw new Error(errorMsg);
    }
};

/**
 * 获取好友请求列表
 * @returns {Promise<Array>} 好友请求列表
 */
export const getFriendRequests = async () => {
    try {
        const response = await apiClient.get('/friends/requests/received');
        if (response.data.code === 200) {
            return response.data.data || []; // 提取data字段，确保返回数组
        } else {
            throw new Error(response.data.message || '获取好友请求列表失败');
        }
    } catch (error) {
        console.error("获取好友请求列表失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '获取好友请求列表失败';
        throw new Error(errorMsg);
    }
};

/**
 * 接受好友请求
 * @param {number} requestId 好友请求ID
 * @returns {Promise<object>} 包含操作结果的响应
 */
export const acceptFriendRequest = async (requestId) => {
    try {
        const response = await apiClient.post(`/friends/requests/${requestId}/accept`);
        if (response.data.code === 200) {
            return response.data.data;
        } else {
            throw new Error(response.data.message || '接受好友请求失败');
        }
    } catch (error) {
        console.error("接受好友请求失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '接受好友请求失败';
        throw new Error(errorMsg);
    }
};

/**
 * 拒绝好友请求
 * @param {number} requestId 好友请求ID
 * @returns {Promise<object>} 包含操作结果的响应
 */
export const rejectFriendRequest = async (requestId) => {
    try {
        const response = await apiClient.post(`/friends/requests/${requestId}/reject`);
        if (response.data.code === 200) {
            return response.data.data;
        } else {
            throw new Error(response.data.message || '拒绝好友请求失败');
        }
    } catch (error) {
        console.error("拒绝好友请求失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '拒绝好友请求失败';
        throw new Error(errorMsg);
    }
}; 