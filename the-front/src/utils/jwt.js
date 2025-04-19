/**
 * 生成测试用的JWT令牌
 * 注意：这只是用于测试目的，实际应用中JWT应该由服务器生成
 */
export const generateTestJwt = () => {
    // 生成一个随机的用户ID (1-1000)
    const userId = Math.floor(Math.random() * 1000) + 1;
    
    // 创建JWT的header部分
    const header = {
        alg: 'HS256',
        typ: 'JWT'
    };
    
    // 创建JWT的payload部分
    const payload = {
        sub: `user${userId}`,
        name: `测试用户${userId}`,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 3600 // 1小时后过期
    };
    
    // 将header和payload进行base64编码
    const encodedHeader = btoa(JSON.stringify(header));
    const encodedPayload = btoa(JSON.stringify(payload));
    
    // 创建一个模拟的签名（实际应用中应该使用密钥进行加密）
    const signature = btoa('testsignature');
    
    // 组合成完整的JWT
    return `${encodedHeader}.${encodedPayload}.${signature}`;
}; 