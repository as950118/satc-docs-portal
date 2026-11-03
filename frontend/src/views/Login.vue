<template>
  <div class="login-container">
    <!-- 배경 비디오 -->
    <video 
      autoplay 
      muted 
      loop 
      class="background-video"
    >
      <source src="/code_background.mp4" type="video/mp4">
    </video>
    
    <!-- 배경 오버레이 -->
    <div class="background-overlay"></div>
    
    <!-- 로그인 폼 -->
    <div class="login-form-container">
      <div class="login-form">
        <!-- 로고 및 타이틀 -->
        <div class="login-header">
          <img src="/logo.svg" alt="Logo" class="login-logo" />
          <h1 class="login-title">문서 관리 시스템</h1>
          <p class="login-subtitle">Document Management System</p>
        </div>
        
        <!-- 로그인 폼 -->
        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          class="login-form-content"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="사용자명을 입력하세요"
              size="large"
              :prefix-icon="User"
              clearable
            />
          </el-form-item>
          
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="비밀번호를 입력하세요"
              size="large"
              :prefix-icon="Lock"
              show-password
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          
          <el-form-item>
            <div class="login-options">
              <el-checkbox v-model="loginForm.rememberMe">로그인 정보 기억</el-checkbox>
            </div>
          </el-form-item>
          
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="isLoading"
              @click="handleLogin"
              class="login-button"
            >
              <span v-if="!isLoading">로그인</span>
              <span v-else>로그인 중...</span>
            </el-button>
          </el-form-item>
        </el-form>
        
        <!-- 추가 링크 -->
        <div class="login-footer">
          <el-link type="primary" :underline="false">비밀번호를 잊으셨나요?</el-link>
        </div>
      </div>
    </div>
    
    <!-- 시스템 정보 -->
    <div class="system-info">
      <div class="system-status">
        <el-icon class="status-icon"><CircleCheck /></el-icon>
        <span>시스템 정상 운영 중</span>
      </div>
      <div class="system-version">
        Version 1.0.0
      </div>
    </div>
  </div>
</template>

<script>
import { authApiMethods, authUtils, safeRedirectPath } from '@/api/auth'
import { CircleCheck, Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

export default {
  name: 'Login',
  components: {
    User,
    Lock,
    CircleCheck
  },
  setup() {
    const router = useRouter()
    const route = useRoute()
    const loginFormRef = ref(null)
    const isLoading = ref(false)
    
    // 로그인 폼 데이터
    const loginForm = reactive({
      username: '',
      password: '',
      rememberMe: false
    })
    
    // 폼 검증 규칙
    const loginRules = {
      username: [
        { required: true, message: '사용자명을 입력해주세요', trigger: 'blur' },
        { min: 3, max: 20, message: '사용자명은 3-20자 사이여야 합니다', trigger: 'blur' }
      ],
      password: [
        { required: true, message: '비밀번호를 입력해주세요', trigger: 'blur' },
        { min: 6, message: '비밀번호는 최소 6자 이상이어야 합니다', trigger: 'blur' }
      ]
    }
    
    // 로그인 처리
    const handleLogin = async () => {
      if (!loginFormRef.value) return
      
      try {
        // 폼 검증
        const isValid = await loginFormRef.value.validate()
        if (!isValid) return
        
        isLoading.value = true
        
        // API 호출
        const response = await authApiMethods.login({
          username: loginForm.username,
          password: loginForm.password,
          rememberMe: loginForm.rememberMe
        })
        
        // 로그인 성공 처리
        if (response.data.status === 200 && response.data.data) {
          // 세션 ID 저장 및 인증 상태 업데이트
          const sessionId = response.data.data.token
          const userInfo = response.data.data.userInfo
          
          authUtils.setAuth(sessionId, userInfo, loginForm.rememberMe)
          
          ElMessage.success('로그인에 성공했습니다!')
          
          await router.push(safeRedirectPath(route.query.redirect))
        } else {
          ElMessage.error(response.data.errorMessage || '로그인에 실패했습니다.')
        }
        
      } catch (error) {
        console.error('로그인 에러:', error)
        
        if (error.response?.status === 401) {
          ElMessage.error('사용자명 또는 비밀번호가 올바르지 않습니다.')
        } else if (error.response?.status === 403) {
          ElMessage.error('계정이 비활성화되었습니다. 관리자에게 문의하세요.')
        } else if (error.response?.status >= 500) {
          ElMessage.error('서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.')
        } else {
          ElMessage.error('네트워크 오류가 발생했습니다. 연결을 확인해주세요.')
        }
      } finally {
        isLoading.value = false
      }
    }
    
    return {
      loginFormRef,
      loginForm,
      loginRules,
      isLoading,
      handleLogin,
      User,
      Lock,
      CircleCheck
    }
  }
}
</script>

<style scoped>
.login-container { position: relative; width: 100vw; height: 100vh; overflow: hidden; display: flex; align-items: center; justify-content: center; background: var(--mt-canvas); font-family: var(--mt-font); }
.background-video { position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover; z-index: 1; display: none; }
.background-overlay { display: none; }
.login-form-container { position: relative; z-index: 3; width: 100%; max-width: 450px; padding: 20px; }
.login-form { background: var(--mt-canvas); border-radius: var(--mt-r-xl); padding: 48px 40px; box-shadow: var(--mt-shadow-sm); border: 1px solid var(--mt-hairline); }
.login-header { text-align: center; margin-bottom: 40px; }
.login-logo { width: 140px; height: auto; margin-bottom: 20px; }
.login-title { color: var(--mt-ink); font-size: 28px; font-weight: 600; margin: 0 0 8px 0; letter-spacing: -0.5px; }
.login-subtitle { color: var(--mt-stone); font-size: 13px; font-weight: 500; margin: 0; letter-spacing: 1px; text-transform: uppercase; }
.login-form-content { margin-top: 30px; }

:deep(.el-form-item) { margin-bottom: 25px; }
:deep(.el-input__wrapper) { border-radius: var(--mt-r-md); box-shadow: none; border: 1px solid var(--mt-hairline); transition: border-color 0.15s ease; }
:deep(.el-input__wrapper:hover) { border-color: var(--mt-stone); box-shadow: none; }
:deep(.el-input__wrapper.is-focus) { border-color: var(--mt-green); box-shadow: 0 0 0 3px var(--mt-green-tint); }
:deep(.el-input__inner) { height: 48px; font-size: 15px; color: var(--mt-ink); }
:deep(.el-input__prefix-inner) { color: var(--mt-stone); }

.login-options { display: flex; justify-content: space-between; align-items: center; margin: 10px 0; }
:deep(.el-checkbox__label) { color: var(--mt-steel); font-size: 14px; }

.login-button { width: 100%; height: 48px; border-radius: var(--mt-r-full); font-size: 15px; font-weight: 600; background: var(--mt-primary); color: var(--mt-on-primary); border: none; box-shadow: none; transition: background 0.15s ease; }
.login-button:hover { background: var(--mt-charcoal); color: var(--mt-on-primary); }

.login-footer { text-align: center; margin-top: 30px; }
:deep(.el-link) { font-size: 14px; color: var(--mt-ink); }

.system-info { position: absolute; bottom: 30px; left: 50%; transform: translateX(-50%); z-index: 3; display: flex; flex-direction: column; align-items: center; gap: 10px; }
.system-status { display: flex; align-items: center; gap: 8px; color: var(--mt-steel); font-size: 14px; font-weight: 500; }
.status-icon { color: var(--mt-green); font-size: 16px; }
.system-version { color: var(--mt-stone); font-size: 12px; font-weight: 400; }

/* 반응형 디자인 */
@media (max-width: 768px) {
  .login-form-container { max-width: 90%; padding: 10px; }
  .login-form { padding: 40px 30px; }
  .login-title { font-size: 24px; }
  .login-logo { width: 120px; }
}
@media (max-width: 480px) {
  .login-form { padding: 30px 20px; }
  .login-title { font-size: 22px; }
  .system-info { bottom: 20px; }
}
</style>
