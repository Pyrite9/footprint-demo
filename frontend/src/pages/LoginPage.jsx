import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { login, loginWithGoogle } from '../api/auth'
import { removeToken, saveToken } from '../api/token'
import GoogleLoginButton from '../components/GoogleLoginButton'

// 구글 계정(학교 계정)으로 로그인합니다. 시작 계정의 이메일·비밀번호 로그인은 개발·백업용으로 아래에 접어 둡니다.
// 성공하면 원래 가려던 주소(없으면 첫 화면)로 이동합니다.
function LoginPage() {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)
    const navigate = useNavigate()
    // RequireLogin이 넘겨준 원래 주소입니다. 주소창에 /login을 직접 쳐서 왔으면 state가 없으므로 첫 화면으로 갑니다.
    const location = useLocation()
    const from = location.state?.from ?? '/'

    // 두 로그인 방법이 같이 쓰는 성공 처리입니다. 받은 token을 저장하고 원래 주소로 갑니다.
    function handleLoginSuccess(data) {
        saveToken(data.accessToken)
        navigate(from, { replace: true })
    }

    // 구글 버튼이 받은 ID token을 서버에 보내 우리 서비스의 token으로 바꿉니다.
    function handleGoogleCredential(credential) {
        setError(null)
        // 이전 token이 남아 있으면 버립니다. 남아 있으면 로그인 실패(401)를 "token 만료"로 잘못 처리하게 됩니다.
        removeToken()

        loginWithGoogle(credential)
            .then((data) => handleLoginSuccess(data))
            .catch((err) => setError(err.message))
    }

    function handleSubmit(event) {
        // form의 기본 동작(페이지 새로고침)을 막습니다.
        event.preventDefault()
        setError(null)
        // 구글 로그인과 같은 이유로 이전 token을 버립니다.
        removeToken()

        login(email, password)
            .then((data) => handleLoginSuccess(data))
            .catch((err) => setError(err.message))
    }

    return (
        // login: GitHub 로그인 화면처럼 좁은 칸을 가운데에 둡니다(index.css).
        <main className="login">
            <h1>발자국 로그인</h1>
            <p className="muted">조별과제 과정기록 & 평가지원 서비스</p>
            <div className="panel login-form">
                <GoogleLoginButton onCredential={handleGoogleCredential} onError={(message) => setError(message)} />
                <p className="muted">학교 계정(g.yju.ac.kr)으로 로그인합니다.</p>
                {error && <p className="error">{error}</p>}
            </div>
            {/* details는 누르면 펼쳐지는 HTML 기본 요소입니다. 펼침 여부를 state로 두지 않아도 됩니다. */}
            <details className="login-fallback">
                <summary>시작 계정으로 로그인 (개발·백업용)</summary>
                <form onSubmit={handleSubmit} className="panel login-form">
                    <input type="email" value={email} placeholder="이메일" onChange={(event) => setEmail(event.target.value)} />
                    <input type="password" value={password} placeholder="비밀번호" onChange={(event) => setPassword(event.target.value)} />
                    <button type="submit">로그인</button>
                </form>
            </details>
        </main>
    )
}

export default LoginPage
