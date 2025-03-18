# 开发中 🚧

目标：构建一款类 Telegram 的即时通讯应用

技术栈：

-   后端：Spring Boot \+ WebSocket \+ Redis \+ MySQL
-   前端：Vue3 \+ Element Plus \+ WebSocket

功能规划：

1.  Web 端：实现 Web 聊天室核心功能
2.  桌面端：借助 Electron 构建桌面应用

## 业务分析

### 一、用户账户系统

#### 1. 注册与认证

-   **需求**：
    -   支持邮箱/用户名\+密码注册  //当前已经实现
    -   邮箱验证激活账户  //已实现
    -   密码复杂度校验（字母、数字、特殊符号）

#### 2. 登录与安全

-   **需求**：
    -   多设备登录管理
    -   登录态保持（JWT Token）
    -   异常登录检测（异地/IP变更）
    -   密码找回（邮箱验证）

### 二、聊天室核心功能

#### 1. 聊天室管理

-   **需求**：
    -   创建公开/私密聊天室
    -   聊天室搜索与分类（按主题、活跃度）
    -   设置聊天室权限（管理员/成员/游客）
    -   聊天室公告编辑与置顶

#### 2. 实时通讯

-   **需求**：
    -   文本消息收发（支持 Markdown/Emoji）
    -   消息撤回（2 分钟内可撤回）
    -   消息状态追踪（已读/未读）
    -   消息历史记录分页加载

#### 3. 文件传输

-   **需求**：
    -   图片/文件上传（限制文件类型和大小）
    -   实时缩略图预览
    -   文件过期清理（7 天未下载自动删除）

### 三、社交关系系统

#### 1. 好友管理

-   **需求**：
    -   用户搜索（ID/昵称/模糊匹配）
    -   好友请求发送与处理
    -   好友分组管理（自定义标签）
    -   好友在线状态显示

#### 2. 私聊与群聊

-   **需求**：
    -   1v1 私聊会话
    -   群聊创建与管理（500 人上限）
    -   @成员提醒功能
    -   聊天记录导出（JSON/PDF）

### 四、系统管理功能

#### 1. 内容监管

-   **需���**：
    -   敏感词实时过滤（动态更新词库）
    -   用户举报机制（消息/聊天室/用户）
    -   违规记录日志追踪
    -   自动封禁策略（3 次违规自动封号）

#### 2. 数据统计

-   **需求**：
    -   在线用户数实时监控
    -   消息量趋势分析（按小时/天）
    -   用户活跃度排行榜
    -   系统资源占用报告

### 五、辅助功能需求

#### 1. 通知系统

-   **需求**：
    -   系统公告推送
    -   好友请求提醒
    -   消息未读提醒
    -   活动弹窗通知

#### 2. 用户体验优化

-   **需求**：
    -   多主题切换（白天/夜间模式）
    -   消息声音提示自定义
    -   输入状态提示（对方正在输入...）
    -   消息翻译功能（支持 20\+ 语言）