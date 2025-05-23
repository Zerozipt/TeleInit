// src/utils/auth.js
// 统一的认证工具类

const authItemName = "authorize";

/**
 * 获取JWT令牌 - 统一接口
 * @returns {string|null} JWT令牌或null
 */
export function getAuthToken() {
    try {
        // 优先从sessionStorage获取（当前会话）
        let authData = sessionStorage.getItem(authItemName);
        
        // 如果sessionStorage没有，再从localStorage获取（记住我）
        if (!authData) {
            authData = localStorage.getItem(authItemName);
        }
        
        if (!authData) {
            return null;
        }
        
        const parsedAuth = JSON.parse(authData);
        
        // 检查令牌是否过期
        if (parsedAuth.expire && new Date(parsedAuth.expire) <= new Date()) {
            clearAuthToken();
            return null;
        }
        
        return parsedAuth?.token || null;
    } catch (error) {
        console.error("Error reading auth token:", error);
        clearAuthToken();
        return null;
    }
}

/**
 * 获取完整的认证数据
 * @returns {Object|null} 包含token、expire、role等信息的对象
 */
export function getAuthData() {
    try {
        // 优先从sessionStorage获取
        let authData = sessionStorage.getItem(authItemName);
        
        // 如果sessionStorage没有，再从localStorage获取
        if (!authData) {
            authData = localStorage.getItem(authItemName);
        }
        
        if (!authData) {
            return null;
        }
        
        const parsedAuth = JSON.parse(authData);
        
        // 检查令牌是否过期
        if (parsedAuth.expire && new Date(parsedAuth.expire) <= new Date()) {
            clearAuthToken();
            return null;
        }
        
        return parsedAuth;
    } catch (error) {
        console.error("Error reading auth data:", error);
        clearAuthToken();
        return null;
    }
}

/**
 * 存储认证令牌
 * @param {boolean} remember - 是否记住登录状态
 * @param {string} token - JWT令牌
 * @param {string} expire - 过期时间
 * @param {string} role - 用户角色
 * @param {string} username - 用户名（可选）
 */
export function setAuthToken(remember, token, expire, role, username = null) {
    const authObj = { token, expire, role };
    if (username) {
        authObj.username = username;
    }
    
    const str = JSON.stringify(authObj);
    
    // 总是存储到sessionStorage（当前会话有效）
    sessionStorage.setItem(authItemName, str);
    
    // 如果勾选"记住我"，额外存储到localStorage（持久化）
    if (remember) {
        localStorage.setItem(authItemName, str);
    } else {
        // 如果没勾选记住我，清除localStorage中的旧数据
        localStorage.removeItem(authItemName);
    }
}

/**
 * 清除认证令牌
 */
export function clearAuthToken() {
    localStorage.removeItem(authItemName);
    sessionStorage.removeItem(authItemName);
}

/**
 * 检查用户是否已登录
 * @returns {boolean}
 */
export function isAuthenticated() {
    return getAuthToken() !== null;
}

/**
 * 获取当前用户信息（从JWT中解析）
 * @returns {Object|null} 用户信息对象
 */
export function getCurrentUser() {
    const authData = getAuthData();
    if (!authData || !authData.token) {
        return null;
    }
    
    try {
        // 如果需要解析JWT payload，可以在这里添加逻辑
        // 目前返回存储的基本信息
        return {
            role: authData.role,
            username: authData.username,
            token: authData.token
        };
    } catch (error) {
        console.error("Error parsing user info:", error);
        return null;
    }
}

/**
 * 创建带认证头的axios配置
 * @returns {Object} axios请求配置对象
 */
export function getAuthHeaders() {
    const token = getAuthToken();
    if (!token) {
        return {};
    }
    
    return {
        'Authorization': `Bearer ${token}`
    };
}

/**
 * 检查是否有管理员权限
 * @returns {boolean}
 */
export function isAdmin() {
    const authData = getAuthData();
    return authData?.role === 'admin';
} 