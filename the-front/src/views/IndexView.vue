<template>
  <div class="app-layout">
    <!-- Navigation Bar -->
    <NavBar />

    <!-- Main Content Area -->
    <div class="main-content">
      <router-view v-slot="{ Component }">
        <keep-alive include="ChatView,ContactsView"> 
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </div>
  </div>
</template>

<script setup>
// Import NavBar component
import NavBar from './components/NavBar.vue';
// Logout logic is now moved to NavBar.vue
// import {logout} from "@/net/index.js";
// import router from "@/router/index.js";

// function userLogout(){
//   logout(() => {
//     router.push('/')
//   })
// }
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden; /* Prevent layout overflow */
}

.main-content {
  flex: 1; /* Takes up remaining space */
  overflow: hidden; /* Important to contain child routes */
  /* The background color will be determined by the child route (ChatView, ContactsView, etc.) */
}

/* Ensure the router-view and the rendered component fill the main content area */
.main-content > :deep(div) { /* Might need adjustment based on router-view output */
  height: 100%;
  width: 100%;
}
:deep(.el-tabs__content) {
    height: calc(100vh - 55px - 40px); /* Example: Adjust based on actual header/tab height */
    overflow-y: auto;
}

:deep(.el-tab-pane) {
    height: 100%;
}

</style>