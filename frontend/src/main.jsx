import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// 한국어 글꼴 Pretendard입니다(npm 패키지 pretendard). 화면에 실제로 나온 글자의 조각 파일만 내려받습니다(dynamic subset).
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import './index.css'
import App from './App.jsx'
import { applySavedTheme } from './theme'

// 기억한 테마(라이트·다크)를 화면을 그리기 전에 붙입니다. 그려진 뒤에 붙이면 잠깐 다른 색이 깜빡입니다.
applySavedTheme()

// index.html의 #root에 App을 그립니다.
// StrictMode는 개발 중 실수를 찾으려고 useEffect 등 일부 코드를 두 번 실행합니다. 개발 모드에서 같은 API 요청이 2번 보이는 이유입니다.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
