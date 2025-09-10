<template>
  <div id="app">
    <el-container style="height: 100vh">
      <!-- 헤더 -->
      <el-header class="premium-header" height="80px">
        <div class="header-background-pattern"></div>
        <div class="header-glow-overlay"></div>
        <div class="header-content">
          <div class="logo-title-area" @click="goToHome">
            <div class="logo-container">
              <img src="/okestro_logo_white.png" alt="Okestro Logo" class="logo-image" />
            </div>
            <div class="title-container">
              <h1 class="main-title">CMP 문서 관리 시스템</h1>
              <span class="title-subtitle">CMP Document Management System</span>
            </div>
          </div>
          <div class="header-actions">
            <div class="status-indicator">
              <div class="status-dot"></div>
              <span class="status-text">온라인</span>
            </div>
          </div>
        </div>
      </el-header>
      
      <!-- 메인 컨텐츠 -->
      <el-container>
        <router-view />
      </el-container>
    </el-container>
  </div>
</template>

<script>
export default {
  name: 'App',
  methods: {
    goToHome() {
      // 현재 경로가 홈이 아닌 경우에만 이동
      if (this.$route.path !== '/') {
        this.$router.push('/')
      }
      // 홈 화면에서 클릭한 경우 페이지 새로고침 효과
      else {
        window.location.reload()
      }
    }
  }
}
</script>

<style>
#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  min-height: 100vh;
}

.premium-header {
  background: linear-gradient(135deg, #000080 0%, #000066 20%, #000050 100%);
  color: white;
  padding: 0 40px;
  box-shadow: 
    0 4px 20px rgba(0, 0, 128, 0.25),
    0 1px 3px rgba(0, 0, 0, 0.12),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  position: relative;
  overflow: hidden;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.header-background-pattern {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: 0.08;
  background-image: 
    linear-gradient(45deg, transparent 40%, rgba(255,255,255,0.1) 50%, transparent 60%),
    radial-gradient(circle at 20% 80%, rgba(255,255,255,0.15) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255,255,255,0.15) 0%, transparent 50%),
    linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.05) 50%, transparent 100%);
  background-size: 120px 120px, 250px 250px, 180px 180px, 100% 100%;
  animation: headerShimmer 15s ease-in-out infinite;
  z-index: 1;
}

.header-glow-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(90deg, 
    transparent 0%, 
    rgba(255, 255, 255, 0.03) 25%, 
    rgba(255, 255, 255, 0.05) 50%, 
    rgba(255, 255, 255, 0.03) 75%, 
    transparent 100%
  );
  animation: headerGlow 8s ease-in-out infinite;
  z-index: 2;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  position: relative;
  z-index: 3;
}

.logo-title-area {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 8px;
  transition: all 0.3s ease;
  user-select: none;
}

.logo-title-area:hover {
  background: rgba(255, 255, 255, 0.1);
  transform: translateY(-1px);
}

.logo-title-area:active {
  transform: translateY(0);
  background: rgba(255, 255, 255, 0.15);
}

.logo-container {
  display: flex;
  align-items: center;
}

.logo-image {
  width: 115px;
  height: 24px;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,0.3));
}

.title-container {
  flex: 1;
  margin-left: 24px;
  display: flex;
  flex-direction: column;
}

.main-title {
  margin: 0;
  font-weight: 600;
  font-size: 24px;
  letter-spacing: 0.5px;
  color: white;
  text-shadow: 0 2px 4px rgba(0,0,0,0.3);
  line-height: 1.2;
}

.title-subtitle {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  font-weight: 400;
  letter-spacing: 1px;
  text-transform: uppercase;
  margin-top: 2px;
}

.header-actions {
  display: flex;
  align-items: center;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 
    0 0 8px rgba(16, 185, 129, 0.6),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
  animation: statusPulse 2s ease-in-out infinite;
}

.status-text {
  font-size: 12px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
}

@keyframes headerShimmer {
  0%, 100% { opacity: 0.08; }
  50% { opacity: 0.12; }
}

@keyframes headerGlow {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

@keyframes statusPulse {
  0%, 100% { 
    transform: scale(1);
    box-shadow: 0 0 8px rgba(16, 185, 129, 0.6);
  }
  50% { 
    transform: scale(1.1);
    box-shadow: 0 0 12px rgba(16, 185, 129, 0.8);
  }
}
</style>

