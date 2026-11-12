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
        // 백엔드의 응답을 브라우저로 넘기는 일을 아래 configure에서 직접 합니다.
        selfHandleResponse: true,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req, res) => {
            // 20MB를 넘는 업로드는 백엔드가 파일을 다 받기 전에 400으로 답합니다.
            // 그 응답을 바로 넘기면 개발 서버가 아직 올라오는 파일을 버리고 연결을 끊어서, 브라우저는 400 대신 "연결이 끊어짐"을 봅니다.
            // 그래서 브라우저가 요청 body를 다 보낸 뒤(req의 end)에 응답을 넘깁니다. 보통 요청은 이미 다 받아서 바로 넘깁니다.
            const sendResponse = () => {
              res.writeHead(proxyRes.statusCode, proxyRes.headers)
              proxyRes.pipe(res)
            }
            if (req.complete) {
              sendResponse()
            } else {
              req.on('end', sendResponse)
              // 백엔드는 이미 답했으므로 남은 body를 백엔드로 보내지 않고(unpipe) 읽어 버립니다(resume). 읽지 않으면 end가 오지 않습니다.
              req.unpipe()
              req.resume()
            }
          })
        },
      },
    },
  },
})
