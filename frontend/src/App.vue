<template>
  <div id="app">
    <el-container style="height: 100vh">
      <!-- 헤더 (로그인된 경우에만 표시) -->
      <el-header v-if="isAuthenticated" class="premium-header" height="64px">
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
              <a v-for="site in toolLinks" :key="site.key" href="#" @click.prevent="openVpnSite(site)" class="site-link vpn-link">
                {{ site.label }} <span class="vpn-text">(VPN)</span>
              </a>
            </div>

            <!-- 실시간 연결이 5초 이상 끊긴 경우 -->
            <span v-if="liveDisconnected" class="live-disconnected" data-test="live-disconnected">실시간 연결 끊김, 재연결 중</span>

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
      <el-container :style="{ height: isAuthenticated ? 'calc(100vh - 64px)' : '100vh' }">
        <router-view />
      </el-container>
    </el-container>
  </div>
</template>

<script>
import { authApiMethods, authUtils } from '@/api/auth'
import { liveDisconnected } from '@/composables/useLiveEvents'
import { toolLinks } from '@/config'
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
    const openVpnSite = (site) => {
      ElMessage.info(`새 창에서 ${site.name}을(를) 열었습니다.`)
      window.open(site.url, '_blank', 'noopener,noreferrer')
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
      toolLinks,
      liveDisconnected,
      openVpnSite,
      showCommonCodeMessage,
      handleLogout,
      updateAuthState
    }
  }
}
</script>

<style>
#app { font-family: var(--mt-font); }
* { margin: 0; padding: 0; box-sizing: border-box; }
body { background: var(--mt-canvas); min-height: 100vh; }

.premium-header {
  background: var(--mt-canvas);
  color: var(--mt-ink);
  padding: 0 32px;
  box-shadow: none;
  position: relative;
  overflow: hidden;
  border-bottom: 1px solid var(--mt-hairline);
}
.header-background-pattern,
.header-glow-overlay { display: none; }

.header-content { display: flex; align-items: center; justify-content: space-between; height: 100%; position: relative; z-index: 3; }
.logo-title-area { display: flex; align-items: center; cursor: pointer; padding: 8px 12px; border-radius: var(--mt-r-md); transition: background 0.15s ease; user-select: none; }
.logo-title-area:hover { background: var(--mt-surface); }
.logo-title-area:active { background: var(--mt-hairline-soft); }
.logo-container { display: flex; align-items: center; }
.title-container { flex: 1; margin-left: 24px; display: flex; flex-direction: column; }
.main-title { margin: 0; font-weight: 600; font-size: 18px; letter-spacing: -0.02em; color: var(--mt-ink); line-height: 1.2; }
.title-subtitle { font-size: 11px; color: var(--mt-stone); font-weight: 500; letter-spacing: 1px; text-transform: uppercase; margin-top: 2px; }
.header-actions { display: flex; align-items: center; gap: 20px; }

.status-indicator { display: flex; align-items: center; gap: 8px; padding: 6px 14px; background: var(--mt-canvas); border-radius: var(--mt-r-full); border: 1px solid var(--mt-hairline); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--mt-green); }
.status-text { font-size: 12px; font-weight: 500; color: var(--mt-steel); }

/* 주요 사이트 링크 스타일 */
.sites-links { display: flex; align-items: center; gap: 15px; margin-right: 20px; }
.sites-label { color: var(--mt-stone); font-size: 14px; font-weight: 500; white-space: nowrap; }
.site-link { color: var(--mt-slate); text-decoration: none; font-size: 14px; font-weight: 500; padding: 4px 8px; border-radius: var(--mt-r-sm); transition: all 0.15s ease; white-space: nowrap; }
.site-link:hover { color: var(--mt-ink); background: var(--mt-surface); text-decoration: none; }
.vpn-text { font-size: 11px; color: var(--mt-warn); font-weight: 400; }

/* 사용자 정보 스타일 */
.user-info { margin-right: 10px; }
.user-display { display: flex; align-items: center; gap: 8px; padding: 6px 14px; background: var(--mt-canvas); border-radius: var(--mt-r-full); border: 1px solid var(--mt-hairline); cursor: pointer; transition: background 0.15s ease; color: var(--mt-ink); font-size: 14px; font-weight: 500; }
.user-display:hover { background: var(--mt-surface); }
.username { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

:deep(.el-dropdown-menu) { background: var(--mt-canvas); border: 1px solid var(--mt-hairline); border-radius: var(--mt-r-md); box-shadow: var(--mt-shadow-md); }
:deep(.el-dropdown-menu__item) { color: var(--mt-slate); font-size: 14px; display: flex; align-items: center; gap: 8px; }
:deep(.el-dropdown-menu__item:hover) { background: var(--mt-surface); color: var(--mt-ink); }
:deep(.el-dropdown-menu__item.is-divided) { border-top: 1px solid var(--mt-hairline); }

/* 반응형 디자인 */
@media (max-width: 1200px) {
  .sites-links { gap: 10px; }
  .site-link { font-size: 13px; padding: 3px 6px; }
  .sites-label { font-size: 13px; }
}
@media (max-width: 992px) {
  .sites-label { display: none; }
  .sites-links { gap: 8px; }
}
.live-disconnected { font-size: 12px; color: var(--mt-error); border: 1px solid var(--mt-error); border-radius: var(--mt-r-sm); padding: 2px 8px; white-space: nowrap; }
</style>


<style>
/* 전역 reset이 목록 기호와 표 선을 지우므로 스펙 미리보기에서만 복원한다. */
.markdown-body ul { list-style: disc; padding-left: 24px; }
.markdown-body ol { list-style: decimal; padding-left: 24px; }
.markdown-body table { border-collapse: collapse; margin: 8px 0; }
.markdown-body th, .markdown-body td { border: 1px solid var(--mt-hairline); padding: 4px 10px; }
</style>
