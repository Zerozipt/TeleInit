<template>
  <div class="app-container">
     <router-view/>
  </div>
</template>

<script setup>
// 引入全局样式
import './assets/global.css';
</script>

<style>
/* 基本样式，确保页面撑满整个窗口 */
html, body {
  margin: 0;
  padding: 0;
  height: 100%;
  width: 100%;
  overflow: hidden;
  font-family: 'Arial', sans-serif;
}

body {
  background-color: #1a1a1a;
  color: #f0f0f0;
}

#app {
  height: 100%;
  width: 100%;
}

.app-container {
  height: 100%;
  width: 100%;
  overflow: hidden;
}
</style>
