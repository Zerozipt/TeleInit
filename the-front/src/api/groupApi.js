// src/api/groupApi.js
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

// 创建 Axios 实例，可以配置基础 URL 等
const apiClient = axios.create({
    baseURL: 'http://localhost:8080/api', // 假设你的 API 基础路径是 /api，如果不是请修改
    timeout: 5000, // 请求超时时间
});

// 添加请求拦截器，自动附加 Authorization Header
apiClient.interceptors.request.use(
    (config) => {
        const token = getAuthToken();
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        } else {
            // 如果没有 token，可能需要取消请求或进行其他处理
            console.warn('No auth token found for API request');
            // return Promise.reject(new Error('用户未认证')); // 可以选择中断请求
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

/**
 * 创建群聊
 * @param {string} groupName 群聊名称
 * @returns {Promise<object|null>} 成功时返回群组信息，失败时返回 null
 */
export const createGroup = async (groupName) => {
    if (!groupName || !groupName.trim()) {
        ElMessage.error('群聊名称不能为空');
        return null;
    }
    try {
        // 注意：后端接口需要接收 { "name": "群聊名称" } 格式的数据
        const response = await apiClient.post('/groups', { name: groupName });
        // 假设后端成功时直接返回 Group DTO，状态码为 201 或 200
        if (response.status === 201 || response.status === 200) {
            ElMessage.success(`群聊 "${response.data.name}" 创建成功！`);
            return response.data; // 返回创建的群组信息
        } else {
            // 虽然 axios 会在非 2xx 时抛错，但以防万一
            ElMessage.error(`创建群聊失败: ${response.data?.error || '未知错误'}`);
            return null;
        }
    } catch (error) {
        console.error("创建群聊 API 请求失败:", error);
        const errorMsg = error.response?.data?.error || error.message || '创建群聊时发生网络或服务器错误';
        ElMessage.error(`创建群聊失败: ${errorMsg}`);
        return null;
    }
};

/**
 * 加入群聊
 * @param {string} groupName 要加入的群聊名称
 * @returns {Promise<object|null>} 成功时返回成员信息，失败时返回 null
 */
export const joinGroup = async (groupName) => {
    if (!groupName || !groupName.trim()) {
        ElMessage.error('群聊名称不能为空');
        return null;
    }
    try {
        // 调用 /api/groups/{groupId}/members 接口
        const response = await apiClient.post(`/groups/${groupName}/members`);
        // 假设后端成功时直接返回 Group_member DTO，状态码为 201 或 200
        if (response.status === 201 || response.status === 200) {
            ElMessage.success(`成功加入群聊 (名称: ${groupName})！`);
            return response.data; // 返回加入的成员信息（虽然可能用处不大，但先返回）
        } else {
            ElMessage.error(`加入群聊失败: ${response.data?.error || '未知错误'}`);
            return null;
        }
    } catch (error) {
        console.error("加入群聊 API 请求失败:", error);
        let errorMsg = '加入群聊时发生网络或服务器错误';
        if (error.response) {
            // 根据后端返回的状态码显示更友好的提示
            if (error.response.status === 404) {
                errorMsg = error.response.data?.error || '群聊不存在';
            } else if (error.response.status === 409) {
                errorMsg = error.response.data?.error || '您已在该群聊中';
            } else {
                errorMsg = error.response.data?.error || error.message;
            }
        } else {
            errorMsg = error.message;
        }
        ElMessage.error(`加入群聊失败: ${errorMsg}`);
        return null;
    }
};