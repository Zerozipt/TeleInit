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
export const addFriend = async (targetUserId, targetUsername) => {
    if (!targetUserId) {
        throw new Error('目标用户ID不能为空');
    }
    try {
        //这个请求携带两个参数，一个是目标用户ID，一个是目标用户名，参数类型是requestBody
        targetUserId = parseInt(targetUserId);
        const response = await apiClient.post('/friends/request', 
            { targetUserId, targetUsername },
            {
                headers: {
                    'Authorization': `Bearer ${getAuthTokenFromStorage()}`
                }
            }
        );
        if (response.data.code === 200) {
            // 添加好友请求成功后，刷新好友请求列表
            // 可以从WebSocket实例中获取当前用户信息
            if (window.stompClientInstance) {
                window.stompClientInstance.refreshFriendRequests()
                    .then(() => console.log("好友请求列表已刷新"))
                    .catch(err => console.error("刷新好友请求列表失败:", err));
            }
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
 * 接受好友请求
 * @param {number} senderId 发送者ID
 * @param {number} receiverId 接收者ID
 * @returns {Promise<object>} 包含操作结果的响应
 */
export const acceptFriendRequest = async (senderId, receiverId) => {
    try {
        const response = await apiClient.post(`/friends/requests/received`, 
            {senderId: senderId, receiverId: receiverId});
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
 * @param {number} senderId 发送者ID
 * @param {number} receiverId 接收者ID
 * @returns {Promise<object>} 包含操作结果的响应
 */
export const rejectFriendRequest = async (senderId, receiverId) => {
    try {
        const response = await apiClient.post(`/friends/requests/reject`, 
            {senderId: senderId, receiverId: receiverId});
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

/**
 * 取消好友请求
 * @param {number} firstUserId 发送请求的用户ID
 * @param {number} secondUserId 接收请求的用户ID
 * @returns {Promise<object>} 包含操作结果的响应
 */
export const cancelFriendRequest = async (firstUserId, secondUserId) => {
    try {
        const response = await apiClient.post(`/friends/requests/cancel`, 
            { targetUserId: secondUserId });
        if (response.data.code === 200) {
            // 取消好友请求成功后，刷新好友请求列表
            if (window.stompClientInstance) {
                window.stompClientInstance.refreshFriendRequests()
                    .then(() => console.log("好友请求列表已刷新"))
                    .catch(err => console.error("刷新好友请求列表失败:", err));
            }
            return response.data.data;
        } else {
            throw new Error(response.data.message || '取消好友请求失败');
        }
    } catch (error) {
        console.error("取消好友请求失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '取消好友请求失败';
        throw new Error(errorMsg);
    }
};

/**
 * 删除好友
 * @param {number} targetUserId 要删除的好友ID
 * @returns {Promise<boolean>} 操作是否成功
 */
export const deleteFriend = async (targetUserId) => {
    if (!targetUserId) {
        ElMessage.warning('目标用户ID不能为空');
        return false;
    }
    try {
        const response = await apiClient.post('/friends/delete', { targetUserId });
        if (response.data.code === 200) {
            ElMessage.success('删除好友成功');
            if (window.stompClientInstance) {
                window.stompClientInstance.refreshFriends()
                    .then(() => console.log('好友列表已刷新'))
                    .catch(err => console.error('刷新好友列表失败:', err));
            }
            return true;
        } else {
            throw new Error(response.data.message || '删除好友失败');
        }
    } catch (error) {
        console.error('删除好友 API 请求失败:', error);
        const msg = error.response?.data?.message || error.message || '删除好友异常';
        ElMessage.error(msg);
        throw new Error(msg);
    }
}; 