import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'

// index.html의 #root에 App을 그립니다.
// StrictMode는 개발 중 실수를 찾으려고 useEffect 등 일부 코드를 두 번 실행합니다. 개발 모드에서 같은 API 요청이 2번 보이는 이유입니다.
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
