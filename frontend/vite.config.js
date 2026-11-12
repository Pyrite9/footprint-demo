import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 화면은 백엔드를 화면과 같은 주소의 /api로 부릅니다(frontend/.env의 VITE_API_URL=/api).
    // 개발 서버가 /api로 온 요청을 받아 앞의 /api를 떼고 백엔드로 넘깁니다. 예: /api/auth/login → http://127.0.0.1:8080/auth/login
    // 이유: 화면(localhost:5173)과 백엔드(127.0.0.1:8080)는 브라우저가 다른 사이트로 봐서 로그인 cookie를 보내지 않습니다.
    // 같은 주소로 부르면 cookie가 항상 실립니다. 배포한 화면은 nginx가 같은 일을 합니다(frontend/nginx.conf).
    proxy: {
      '/api': {
        // localhost가 아니라 127.0.0.1입니다. Windows에서 localhost가 IPv6(::1)로 연결되면 큰 업로드 뒤에 멈춘 일이 있었습니다.
        target: 'http://127.0.0.1:8080',
        rewrite: (path) => path.replace(/^\/api/, ''),
        // 개발 서버에서만 다른 점: 20MB를 넘는 업로드는 400 JSON 대신 연결이 끊겨서 화면에 "요청을 처리하지 못했습니다"가 나옵니다.
        // 백엔드가 파일을 다 받기 전에 400으로 답하면 개발 서버가 올라오던 연결을 닫기 때문입니다. 배포한 화면(nginx)은 400 JSON을 줍니다.
      },
    },
  },
})
