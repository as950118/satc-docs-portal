<template>
  <div id="app">
    <el-container style="height: 100vh">
      <!-- 헤더 (로그인된 경우에만 표시) -->
      <el-header v-if="isAuthenticated" class="premium-header" height="80px">
        <div class="header-background-pattern"></div>
        <div class="header-glow-overlay"></div>
        <div class="header-content">
          <div class="logo-title-area" @click="goToHome">
            <div class="logo-container">
              <img src="/logo.svg" alt="Logo" class="logo-image" />
            </div>
            <div class="title-container">
              <h1 class="main-title">문서 관리 시스템</h1>
              <span class="title-subtitle">Document Management System</span>
            </div>
          </div>
          <div class="header-actions">
            <!-- 주요 사이트 링크들 -->
            <div class="sites-links">
              <router-link to="/specs" class="site-link">PRD</router-link>
              <a href="#" @click.prevent="openVpnSite('jenkins')" class="site-link vpn-link">
                Jenkins <span class="vpn-text">(VPN)</span>
              </a>
              <a href="#" @click.prevent="openVpnSite('argocd')" class="site-link vpn-link">
                ArgoCD <span class="vpn-text">(VPN)</span>
              </a>
              <a href="#" @click.prevent="openVpnSite('common-code')" class="site-link vpn-link">
                공통코드 <span class="vpn-text">(VPN)</span>
              </a>
            </div>

            <!-- 사용자 정보 -->
            <div class="user-info" v-if="userInfo">
              <el-dropdown @command="handleUserCommand">
                <span class="user-display">
                  <el-icon><User /></el-icon>
                  <span class="username">{{ userInfo.fullName }} 님</span>
                  <el-icon><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <!-- <el-dropdown-item command="profile"> -->
                      <!-- <el-icon><User /></el-icon> -->
                      <!-- 프로필 -->
                    <!-- </el-dropdown-item> -->
                    <!-- <el-dropdown-item command="settings"> -->
                      <!-- <el-icon><Setting /></el-icon> -->
                      <!-- 설정 -->
                    <!-- </el-dropdown-item> -->
                    <el-dropdown-item divided command="logout">
                      <el-icon><SwitchButton /></el-icon>
                      로그아웃
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>
      </el-header>
      
      <!-- 메인 컨텐츠 -->
      <el-container :style="{ height: isAuthenticated ? 'calc(100vh - 80px)' : '100vh' }">
        <router-view />
      </el-container>
    </el-container>
  </div>
</template>

<script>
import { authApiMethods, authUtils } from '@/api/auth'
import { ArrowDown, Setting, SwitchButton, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

export default {
  name: 'App',
  components: {
    User,
    ArrowDown,
    Setting,
    SwitchButton
  },
  setup() {
    const router = useRouter()
    const route = useRoute()
    const userInfo = ref(null)
    const authState = ref(authUtils.isAuthenticated())

    // 인증 상태 확인 (reactive)
    const isAuthenticated = computed(() => {
      return authState.value
    })

    // 인증 상태 업데이트 함수
    const updateAuthState = () => {
      authState.value = authUtils.isAuthenticated()
      if (authState.value) {
        loadUserInfo()
      } else {
        userInfo.value = null
      }
    }

    // 사용자 정보 로드
    const loadUserInfo = () => {
      userInfo.value = authUtils.getUserInfo()
    }

    // 홈으로 이동
    const goToHome = () => {
      if (route.path !== '/') {
        router.push('/')
      } else {
        window.location.reload()
      }
    }

    // 사용자 메뉴 명령 처리
    const handleUserCommand = async (command) => {
      switch (command) {
        case 'profile':
          ElMessage.info('프로필 기능은 준비 중입니다.')
          break
        case 'settings':
          ElMessage.info('설정 기능은 준비 중입니다.')
          break
        case 'logout':
          await handleLogout()
          break
      }
    }

    // VPN 사이트 열기
    const openVpnSite = (siteType) => {
      const sites = {
        'jenkins': {
          url: 'http://172.20.30.230:8080',
          name: 'Jenkins'
        },
        'argocd': {
          url: 'http://172.20.30.169:32587/login',
          name: 'ArgoCD'
        },
        'common-code': {
          url: 'http://172.20.30.63:3000/',
          name: '공통 코드 관리 시스템'
        }
      }

      const site = sites[siteType]
      if (site) {
        ElMessage.info(`새 창에서 ${site.name}을(를) 열었습니다.`)
        window.open(site.url, '_blank', 'noopener,noreferrer')
      }
    }

    // 공통코드 메시지 표시
    const showCommonCodeMessage = () => {
      ElMessage.info('연동 예정입니다.')
    }

    // 로그아웃 처리
    const handleLogout = async () => {
      try {
        // 서버에 로그아웃 요청
        try {
          await authApiMethods.logout()
        } catch (error) {
          console.warn('서버 로그아웃 요청 실패:', error)
          // 서버 요청이 실패해도 로컬 로그아웃은 진행
        }

        // 로컬 인증 정보 제거
        authUtils.logout()
        
        // 인증 상태 업데이트
        updateAuthState()
        
        ElMessage.success('로그아웃되었습니다.')
        
        // 로그인 페이지로 이동
        await router.push('/login')
      } catch (error) {
        console.error('로그아웃 처리 중 오류:', error)
      }
    }

    // 컴포넌트 마운트 시 인증 상태 및 사용자 정보 로드
    onMounted(() => {
      updateAuthState()
      
      // 인증 상태 변경 이벤트 리스너 추가
      window.addEventListener('authStateChange', updateAuthState)
    })

    // 컴포넌트 언마운트 시 이벤트 리스너 제거
    onUnmounted(() => {
      window.removeEventListener('authStateChange', updateAuthState)
    })

    // 라우트 변경 감지하여 인증 상태 업데이트
    router.afterEach(() => {
      updateAuthState()
    })

    return {
      isAuthenticated,
      userInfo,
      goToHome,
      handleUserCommand,
      openVpnSite,
      showCommonCodeMessage,
      handleLogout,
      updateAuthState
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
  gap: 20px;
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

/* 주요 사이트 링크 스타일 */
.sites-links {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-right: 20px;
}

.sites-label {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
}

.site-link {
  color: rgba(255, 255, 255, 0.9);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.site-link:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.1);
  text-decoration: none;
}

.vpn-text {
  font-size: 11px;
  color: rgba(255, 152, 0, 0.9);
  font-weight: 400;
}

/* 사용자 정보 스타일 */
.user-info {
  margin-right: 10px;
}

.user-display {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  cursor: pointer;
  transition: all 0.3s ease;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  font-weight: 500;
}

.user-display:hover {
  background: rgba(255, 255, 255, 0.15);
  transform: translateY(-1px);
}

.username {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.el-dropdown-menu) {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 0, 128, 0.1);
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

:deep(.el-dropdown-menu__item) {
  color: #333;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.el-dropdown-menu__item:hover) {
  background: rgba(0, 0, 128, 0.05);
  color: #000080;
}

:deep(.el-dropdown-menu__item.is-divided) {
  border-top: 1px solid rgba(0, 0, 128, 0.1);
}

/* 반응형 디자인 */
@media (max-width: 1200px) {
  .sites-links {
    gap: 10px;
  }
  
  .site-link {
    font-size: 13px;
    padding: 3px 6px;
  }
  
  .sites-label {
    font-size: 13px;
  }
}

@media (max-width: 992px) {
  .sites-label {
    display: none;
  }
  
  .sites-links {
    gap: 8px;
  }
}
</style>

