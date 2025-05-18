import axios from "axios";
import {ElMessage} from "element-plus";
import router from "@/router";
// import { WebSocket } from 'ws';
const authItemName = "authorize"

const accessHeader = () => {
    return {
        'Authorization': `Bearer ${takeAccessToken()?.token}`
    }
}

const defaultError = (error) => {
    console.error(error)
    const status = error.response.status
    if (status === 429) {
        ElMessage.error(error.response.data.message)
    } else {
        ElMessage.error('发生了一些错误，请联系管理员')
    }
}

const defaultFailure = (message, status, url) => {
    console.warn(`请求地址: ${url}, 状态码: ${status}, 错误信息: ${message}`)
    ElMessage.warning(message)
}

function takeAccessToken() {
    const str = localStorage.getItem(authItemName) || sessionStorage.getItem(authItemName);
    if(!str){
        return null
    }
    const authObj = JSON.parse(str)
    if(authObj.expire <= new Date()) {
        deleteAccessToken()
        ElMessage.warning("登录状态已过期，请重新登录！")
        return null
    }
    return authObj
}

function storeAccessToken(remember, token, expire, role){
    const authObj = { token, expire, role }
    const str = JSON.stringify(authObj)
    if(remember)
        localStorage.setItem(authItemName, str)
    else
        sessionStorage.setItem(authItemName, str)
}

function deleteAccessToken(redirect = false) {
    localStorage.removeItem(authItemName)
    sessionStorage.removeItem(authItemName)
    if(redirect) {
        router.push({ name: 'welcome-login' })
    }
}

function internalPost(url, data, headers, success, failure, error = defaultError){
    axios.post(url, data, { headers: headers }).then(({data}) => {
        if(data.code === 200) {
            success(data.data)
        } else if(data.code === 401) {
            failure('登录状态已过期，请重新登录！')
            deleteAccessToken(true)
        } else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}

function internalGet(url, headers, success, failure, error = defaultError){
    axios.get(url, { headers: headers }).then(({data}) => {
        if(data.code === 200) {
            success(data.data)
        } else if(data.code === 401) {
            failure('登录状态已过期，请重新登录！')
            deleteAccessToken(true)
        } else {
            failure(data.message, data.code, url)
        }
    }).catch(err => error(err))
}

function login(username, password, remember, success, failure = defaultFailure){
    internalPost('/api/auth/login', {
        username: username,
        password: password
    }, {
        'Content-Type': 'application/x-www-form-urlencoded'
    }, (data) => {
        storeAccessToken(remember, data.token, data.expire, data.role)
        ElMessage.success(`登录成功，欢迎 ${data.username} 来到我们的系统`)
        success(data)
    }, failure)
}

function register(username, password, email, code, success, failure = defaultFailure){
    internalPost('/api/auth/register', { 
        username: username,
        password: password,
        email: email,
        code: code
    }, {
        //这里要直接传输json数据,因为后端的@RequestBody注解会自动将json数据转换为对象
        'Content-Type': 'application/json'
    }, (data) => {
        ElMessage.success(`注册成功，欢迎 ${data.username} 来到我们的系统`)
        success(data)
    }, (message) => {
        ElMessage.error(message)
    })
}

function post(url, data, success, failure = defaultFailure) {
    internalPost(url, data, accessHeader() , success, failure)
}

function logout(success, failure = defaultFailure){
    get('/api/auth/logout', () => {
        deleteAccessToken()
        ElMessage.success(`退出登录成功，欢迎您再次使用`)
        success()
    }, failure=>{
        deleteAccessToken()
        router.push({ name: 'welcome-login' })
        ElMessage.error(failure)
    })
}

function get(url, success, failure = defaultFailure) {
    internalGet(url, accessHeader(), success, failure)
}

function isUnauthorized() {
    return !takeAccessToken()
}

function isRoleAdmin() {
    return takeAccessToken()?.role === 'admin'
}

function resetPassword(email, code, password, success, failure = defaultFailure){
    const params = new URLSearchParams();
    params.append('email', email);
    params.append('code', code);
    params.append('password', password);
    
    internalPost('/api/auth/reset-password', params, {
        'Content-Type': 'application/x-www-form-urlencoded'
    }, success, failure)
}

function askVerifyCode(type, email) {
    return get(`/api/auth/ask-code?type=${type}&email=${email}`, () => {
    }, (message) => {
        ElMessage.error(message)
    })
}

// 创建一个WebSocket连接的函数而不是直接赋值给WebSocket
function createChatWebSocket(userId) {
    return new WebSocket(`ws://localhost:8080/chat/${userId}`);
}

// 导出WebSocket创建函数
export { post, get, login, logout, isUnauthorized, isRoleAdmin, accessHeader, register, askVerifyCode, resetPassword, createChatWebSocket }