import axios from 'axios';

// 上传文件到后端并返回文件信息
export async function uploadFile(file) {
  console.log('[fileApi] 上传文件：', file);
  const formData = new FormData();
  formData.append('file', file);
  try {
    // 从 localStorage 获取 token
    const authData = localStorage.getItem('authorize');
    let token = null;
    if (authData) {
      const parsedAuth = JSON.parse(authData);
      token = parsedAuth?.token;
    }

    const response = await axios.post(
      '/api/files/upload',
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
          // 如果存在 token，则添加到 Authorization 请求头
          ...(token && { 'Authorization': `Bearer ${token}` })
        }
      }
    );
    console.log('[fileApi] 完整上传响应对象：', response);
    console.log('[fileApi] 上传响应 response.data：', response.data);
    return response.data; // { fileUrl, fileName }
  } catch (error) {
    console.error('文件上传失败 axios error:', error);
    if (error.response) {
      console.error('[fileApi] 错误响应数据:', error.response.data);
      console.error('[fileApi] 错误响应状态:', error.response.status);
      console.error('[fileApi] 错误响应头:', error.response.headers);
      if (error.response.status === 401) {
        console.error('认证失败，请检查JWT token是否有效或已提供。');
      }
    }
    throw error;
  }
} 