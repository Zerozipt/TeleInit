import axios from 'axios';
import { getAuthToken } from '@/utils/auth';

// 创建 Axios 实例
const apiClient = axios.create({
    baseURL: 'http://localhost:8080',
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
 * 获取用户个人资料
 */
export const getUserProfile = () => {
  return apiClient({
    url: '/api/user/settings/profile',
    method: 'GET'
  })
}

/**
 * 更新用户名
 */
export const updateUsername = (newUsername) => {
  return apiClient({
    url: '/api/user/settings/username',
    method: 'PUT',
    data: {
      newUsername
    }
  })
}

/**
 * 发送密码修改验证码
 */
export const sendPasswordChangeCode = (email) => {
  return apiClient({
    url: '/api/user/settings/password/send-code',
    method: 'GET',
    params: { email }
  })
}

/**
 * 更新密码
 */
export const updatePassword = (email, verificationCode, newPassword) => {
  return apiClient({
    url: '/api/user/settings/password',
    method: 'PUT',
    data: {
      email,
      verificationCode,
      newPassword
    }
  })
} 