// 이전 화면(브라우저 히스토리)으로 돌아간다. 앱 안에서 이동해 온 기록이 없으면(직접 진입/새로고침 직후 등) fallback 으로 이동한다.
export const goBack = (router, fallback = '/') => {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push(fallback)
  }
}
