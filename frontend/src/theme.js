// 화면 테마(라이트·다크)를 정하고 기억하는 함수들입니다.
// 테마는 <html data-theme="light|dark"> 속성으로 정하고, index.css가 이 속성을 보고 색을 바꿉니다.
// 고른 적이 없으면 속성을 두지 않습니다. 그러면 index.css가 운영체제 설정(prefers-color-scheme)을 따릅니다.

const THEME_KEY = 'theme'

// 사용자가 고른 테마('light' 또는 'dark')를 돌려줍니다. 고른 적이 없으면 null입니다.
// localStorage는 브라우저를 닫아도 남습니다. 로그인 token(sessionStorage)과 달리 탭끼리 같은 값을 씁니다.
export function getSavedTheme() {
  return localStorage.getItem(THEME_KEY)
}

// 지금 화면에 보이는 테마를 돌려줍니다. 고른 적이 없으면 운영체제 설정을 읽습니다.
export function getCurrentTheme() {
  const saved = getSavedTheme()
  if (saved) {
    return saved
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

// 테마를 화면에 적용하고 기억합니다.
export function saveTheme(theme) {
  localStorage.setItem(THEME_KEY, theme)
  applySavedTheme()
}

// 기억한 테마를 <html>에 붙입니다. 화면이 처음 열릴 때 main.jsx가 한 번 부릅니다.
export function applySavedTheme() {
  const saved = getSavedTheme()
  if (saved) {
    document.documentElement.dataset.theme = saved
  }
}
