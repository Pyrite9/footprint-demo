// 구글 로그인 버튼에 필요한 Google Identity Services script를 불러옵니다.
// npm 패키지 없이 구글이 주는 script 한 개만 씁니다. 불러오면 window.google.accounts.id를 쓸 수 있습니다.

const GOOGLE_SCRIPT_URL = 'https://accounts.google.com/gsi/client'

// 불러오는 중이거나 다 불러온 Promise를 기억합니다. 로그인 화면을 여러 번 열어도 script 태그는 하나만 붙입니다.
let scriptPromise = null

// script를 불러오고, 다 불러오면 끝나는 Promise를 돌려줍니다. 실패하면 오류 메시지를 담아 reject합니다.
export function loadGoogleScript() {
  if (scriptPromise === null) {
    scriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = GOOGLE_SCRIPT_URL
      script.async = true
      script.onload = () => resolve()
      script.onerror = () => {
        // 다음에 로그인 화면을 열 때 다시 시도하도록 기억을 지웁니다.
        scriptPromise = null
        script.remove()
        reject(new Error('구글 로그인 버튼을 불러오지 못했습니다. 인터넷 연결을 확인해 주세요'))
      }
      document.head.appendChild(script)
    })
  }
  return scriptPromise
}
