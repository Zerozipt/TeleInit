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
 * Searches for groups by name (supports fuzzy search).
 * @param {string} groupName The name of the group to search for.
 * @returns {Promise<Array<object>|null>} An array of group objects if found, otherwise null or an empty array.
 */
export const searchGroupByName = async (groupName) => {
    if (!groupName || !groupName.trim()) {
        ElMessage.warning('请输入要搜索的群组名称');
        return null; 
    }
    try {
        const response = await apiClient.post('/groups/getGroupList', { groupName: groupName.trim() });
        // Backend returns RestBean; response.data is the RestBean object.
        // response.data.data should be the array of groups.
        if (response.data && Array.isArray(response.data.data)) {
            // Even if the array is empty, it's a successful search in terms of API call.
            // The component will handle displaying "no results found".
            return response.data.data; 
        } else if (response.data && response.data.message && response.status === 200 && response.data.data === null) {
            // Case: API call successful (HTTP 200), RestBean indicates no data / logical error (e.g., group not found but not an exception)
            // ElMessage.info(response.data.message); // Component can show a generic "not found" message.
            return []; // Return empty array for consistency if data is explicitly null from a successful call
        } else {
            // Unexpected response structure from a successful HTTP call
            ElMessage.error('搜索群组时收到非预期的响应格式');
            return null;
        }
    } catch (error) {
        console.error("搜索群组 API 请求失败:", error);
        const errorMsg = error.response?.data?.message || 
                         error.message || 
                         '搜索群组时发生网络或服务器错误';
        ElMessage.error(`搜索群组失败: ${errorMsg}`);
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
        ElMessage.warning('群聊名称不能为空');
        return null; // Return null for consistency
    }
    try {
        // 调用 /api/groups/{groupName}/members 接口 (assuming groupName is still used for joining by name)
        const response = await apiClient.post(`/groups/${groupName}/members`);
        // 假设后端成功时直接返回 Group_member DTO，状态码为 201 或 200
        if (response.status === 201 || response.status === 200) {
            // ElMessage.success(`成功加入群组 (名称: ${groupName})！`); // Success message handled in component
            return response.data; 
        } else {
            // This else might not be hit if non-2xx status codes are caught by the catch block
            ElMessage.error(`加入群组失败: ${response.data?.error || response.data?.message || '未知错误'}`);
            return null;
        }
    } catch (error) {
        console.error("加入群聊 API 请求失败:", error);
        let errorMsg = '加入群聊时发生网络或服务器错误';
        if (error.response) {
            errorMsg = error.response.data?.message || error.response.data?.error || error.message;
            // Specific status code messages can be refined here if needed
            // if (error.response.status === 404) {
            //     errorMsg = error.response.data?.message || '群聊不存在';
            // } else if (error.response.status === 409) {
            //     errorMsg = error.response.data?.message || '您已在该群聊中';
            // }
        } else {
            errorMsg = error.message;
        }
        ElMessage.error(`加入群聊失败: ${errorMsg}`);
        return null; // Return null for consistency
    }
};

/**
 * 获取群组详情（包含所有成员和角色）
 * @param {string} groupId 群组ID
 * @returns {Promise<object|null>} 成功时返回群组详情，失败时返回null
 */
export const getGroupDetail = async (groupId) => {
    if (!groupId) {
        ElMessage.warning('群组ID不能为空');
        return null;
    }
    
    try {
        const response = await apiClient.get(`/groups/${groupId}/detail`);
        
        if (response.data && response.data.code === 200) {
            return response.data.data;
        } else {
            ElMessage.error(response.data?.message || '获取群组详情失败');
            return null;
        }
    } catch (error) {
        console.error("获取群组详情失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '获取群组详情失败';
        ElMessage.error(errorMsg);
        return null;
    }
};

/**
 * 邀请好友加入群组
 * @param {string} groupId 群组ID
 * @param {number} inviteeId 被邀请者ID
 * @returns {Promise<boolean>} 是否发送成功
 */
export const inviteUserToGroup = async (groupId, inviteeId) => {
    if (!groupId || !inviteeId) {
        ElMessage.warning('群组ID和被邀请者ID不能为空');
        return false;
    }
    
    try {
        const response = await apiClient.post('/groups/invitations', { 
            groupId: groupId,
            inviteeId: inviteeId
        });
        
        if (response.data && response.data.code === 200) {
            ElMessage.success('邀请发送成功');
            return true;
        } else {
            ElMessage.error(response.data?.message || '邀请发送失败');
            return false;
        }
    } catch (error) {
        console.error("邀请好友加入群组失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '邀请发送失败';
        ElMessage.error(errorMsg);
        return false;
    }
};

/**
 * 获取收到的群组邀请
 * @returns {Promise<Array|null>} 成功时返回邀请列表，失败时返回null
 */
export const getReceivedGroupInvitations = async () => {
    try {
        const response = await apiClient.get('/groups/invitations/received');
        
        if (response.data && response.data.code === 200) {
            // 确保所有邀请对象都有规范化的状态值
            const invitations = response.data.data || [];
            
            // 标准化状态字段
            return invitations.map(invitation => {
                const status = invitation.status?.toLowerCase?.() || '';
                
                // 标准化为前端期望的三种状态
                if (status === '' || status === 'pending' || status === 'requested') {
                    invitation.status = 'pending';
                } else if (status === 'accepted' || status === 'joined') {
                    invitation.status = 'accepted';
                } else if (status === 'rejected' || status === 'declined' || status === 'refused' || status === 'denied') {
                    invitation.status = 'rejected';
                } else {
                    // 默认为待处理状态
                    invitation.status = 'pending';
                }
                
                return invitation;
            });
            
        } else {
            console.error("获取群组邀请失败:", response.data?.message);
            return [];
        }
    } catch (error) {
        console.error("获取群组邀请失败:", error);
        return [];
    }
};

/**
 * 响应群组邀请（接受/拒绝）
 * @param {number} invitationId 邀请ID
 * @param {string} action 操作类型 'accept' 或 'reject'
 * @returns {Promise<boolean>} 操作是否成功
 */
export const respondToGroupInvitation = async (invitationId, action) => {
    if (!invitationId || !action) {
        ElMessage.warning('邀请ID和操作类型不能为空');
        return false;
    }
    
    try {
        const response = await apiClient.post(`/groups/invitations/${invitationId}/${action}`);
        
        if (response.data && response.data.code === 200) {
            ElMessage.success(action === 'accept' ? '已接受邀请' : '已拒绝邀请');
            return true;
        } else {
            ElMessage.error(response.data?.message || '操作失败');
            return false;
        }
    } catch (error) {
        console.error("响应群组邀请失败:", error);
        const errorMsg = error.response?.data?.message || error.message || '操作失败';
        ElMessage.error(errorMsg);
        return false;
    }
};

/**
 * 退出群组
 * @param {string} groupId 群组ID
 * @returns {Promise<boolean>} 操作是否成功
 */
export const exitGroup = async (groupId) => {
    if (!groupId) {
        ElMessage.warning('群组ID不能为空');
        return false;
    }
    try {
        const response = await apiClient.post(`/groups/${groupId}/exit`);
        if (response.data && response.data.code === 200) {
            ElMessage.success('已退出群组');
            if (window.stompClientInstance) {
                window.stompClientInstance.refreshGroups()
                    .then(() => console.log('群组列表已刷新'))
                    .catch(err => console.error('刷新群组列表失败:', err));
            }
            return true;
        } else {
            throw new Error(response.data.message || '退出群组失败');
        }
    } catch (error) {
        console.error('退出群组 API 请求失败:', error);
        const msg = error.response?.data?.message || error.message || '退出群组异常';
        ElMessage.error(msg);
        throw new Error(msg);
    }
};