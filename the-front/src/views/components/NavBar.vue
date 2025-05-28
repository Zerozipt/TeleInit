<template>
  <div class="nav-bar">
    <div class="nav-items">
      <!-- User Avatar/Profile Placeholder -->
      <div class="nav-item user-avatar-placeholder">
        <el-avatar size="large">{{ userInitial }}</el-avatar>
      </div>

      <!-- Chat Link -->
      <router-link to="/index/chat" class="nav-item" active-class="active">
        <el-icon><ChatDotRound /></el-icon>
        <span>聊天</span>
      </router-link>

      <!-- Contacts Link -->
      <router-link to="/index/contacts" class="nav-item" active-class="active">
        <el-icon><User /></el-icon>
        <span>通讯录</span>
      </router-link>

      <!-- Add other main navigation items here -->

    </div>

    <!-- Bottom items like Settings and Logout -->
    <div class="nav-items-bottom">
       <!-- Settings -->
       <router-link to="/index/settings" class="nav-item" active-class="active">
         <el-icon><Setting /></el-icon>
         <span>设置</span>
       </router-link>

       <!-- Logout Button -->
       <div class="nav-item logout-item" @click="userLogout">
         <el-icon><SwitchButton /></el-icon>
         <span>退出</span>
       </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { logout } from "@/net/index.js"; // Ensure path is correct
import router from "@/router/index.js"; // Ensure path is correct
import { ChatDotRound, User, Setting, SwitchButton } from '@element-plus/icons-vue';
import stompClientInstance from '@/net/websocket'; // Import to get username

// Get username initial for avatar placeholder
const userInitial = computed(() => {
    const username = stompClientInstance.currentUser.value;
    return username ? username.substring(0, 1).toUpperCase() : 'U';
});

function userLogout(){
  logout(() => {
    stompClientInstance.disconnect();
    router.push('/')
  })
}
</script>

<style scoped>
.nav-bar {
  width: 80px; /* Adjust width as needed */
  height: 100vh;
  background-color: #303133; /* Dark background */
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  padding: 20px 0;
  box-sizing: border-box;
}

.nav-items,
.nav-items-bottom {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 60px; /* Width of the clickable area */
  height: 60px; /* Height of the clickable area */
  margin-bottom: 15px;
  color: #c0c4cc; /* Default icon/text color */
  cursor: pointer;
  text-decoration: none;
  border-radius: 8px; /* Rounded corners */
  transition: background-color 0.2s, color 0.2s;
}

.nav-item .el-icon {
  font-size: 24px; /* Icon size */
  margin-bottom: 4px;
}

.nav-item span {
  font-size: 12px; /* Text size */
}

.nav-item:hover {
  background-color: #404244; /* Slightly lighter background on hover */
  color: #fff; /* White text/icon on hover */
}

/* Active link style */
.nav-item.active {
  background-color: #4a4c4e; /* Background for active item */
  color: #409EFF; /* Highlight color for active item */
}

.user-avatar-placeholder {
    margin-bottom: 30px; /* More space below avatar */
    cursor: default; /* Not clickable for now */
}
.user-avatar-placeholder:hover {
    background-color: transparent; /* No hover effect */
}
.user-avatar-placeholder .el-avatar {
    background-color: #409EFF; /* Example avatar background */
}


.logout-item {
  /* Specific styles for logout if needed */
}
</style> 