<template>
  <div class="welcome-screen">
    <div class="welcome-content">
      <div class="welcome-icon">
        <el-icon><el-icon-chat-dot-round /></el-icon>
      </div>
      <h2 class="gradient-text">欢迎使用Chat</h2>
      <p>选择一个联系人或群组开始聊天</p>
      
      <div class="floating-shapes">
        <div class="shape circle"></div>
        <div class="shape square"></div>
        <div class="shape triangle"></div>
        <div class="shape circle small"></div>
        <div class="shape square small"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
// No specific script logic needed for this simple component
import { ChatDotRound as ElIconChatDotRound } from '@element-plus/icons-vue';
</script>

<style scoped>
.welcome-screen {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--text-secondary);
  text-align: center;
  background-color: var(--primary-color);
  position: relative;
  overflow: hidden;
  background-image: 
    radial-gradient(circle at 30% 20%, rgba(0, 184, 255, 0.07) 0%, transparent 45%),
    radial-gradient(circle at 70% 60%, rgba(0, 184, 255, 0.07) 0%, transparent 45%);
}

.welcome-content {
  position: relative;
  z-index: 2;
  animation: fadeIn 1s ease-out;
  max-width: 500px;
  padding: 30px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.welcome-icon {
  font-size: 72px;
  margin-bottom: 30px;
  color: var(--accent-color);
  animation: pulse 3s infinite ease-in-out;
  opacity: 0.9;
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 1; text-shadow: 0 0 10px rgba(0, 184, 255, 0.5); }
  50% { transform: scale(1.1); opacity: 0.8; text-shadow: 0 0 20px rgba(0, 184, 255, 0.8); }
  100% { transform: scale(1); opacity: 1; text-shadow: 0 0 10px rgba(0, 184, 255, 0.5); }
}

.welcome-screen h2 {
  margin: 10px 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 1px;
  margin-bottom: 16px;
}

.welcome-screen p {
  font-size: 18px;
  margin-top: 16px;
  opacity: 0.8;
  line-height: 1.6;
}

/* Floating shapes animation */
.floating-shapes {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
  pointer-events: none;
}

.shape {
  position: absolute;
  opacity: 0.2;
  animation: float 10s infinite ease-in-out;
}

.circle {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: var(--accent-color);
  top: 20%;
  left: 30%;
  animation-delay: 0s;
}

.square {
  width: 40px;
  height: 40px;
  background: var(--accent-color);
  top: 60%;
  right: 30%;
  animation-delay: 2s;
  transform: rotate(45deg);
}

.triangle {
  width: 0;
  height: 0;
  border-left: 30px solid transparent;
  border-right: 30px solid transparent;
  border-bottom: 52px solid var(--accent-color);
  top: 40%;
  right: 60%;
  animation-delay: 4s;
}

.small {
  transform: scale(0.5);
  animation-duration: 7s;
}

.circle.small {
  top: 70%;
  left: 20%;
}

.square.small {
  top: 30%;
  right: 15%;
}

@keyframes float {
  0%, 100% { transform: translateY(0) rotate(0); }
  25% { transform: translateY(-20px) rotate(5deg); }
  50% { transform: translateY(0) rotate(0); }
  75% { transform: translateY(20px) rotate(-5deg); }
}
</style> 