import { useState } from 'react'
import { useNavigate } from 'react-router'
import { login } from '../api/auth'
import { removeToken, saveToken } from '../api/token'

// 이메일과 비밀번호를 받아 로그인합니다. 성공하면 첫 화면으로 이동합니다.
function LoginPage() {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState(null)
    const navigate = useNavigate()

    function handleSubmit(event) {
        // form의 기본 동작(페이지 새로고침)을 막습니다.
        event.preventDefault()
        setError(null)
        // 이전 token이 남아 있으면 버립니다. 남아 있으면 로그인 실패(401)를 "token 만료"로 잘못 처리하게 됩니다.
        removeToken()

        login(email, password)
            .then((data) => {
                saveToken(data.accessToken)
                navigate('/')
            })
            .catch((err) => setError(err.message))
    }

    return (
        <main>
            <h1>발자국 로그인</h1>
            <form onSubmit={handleSubmit}>
                <input type="email" value={email} placeholder="이메일" onChange={(event) => setEmail(event.target.value)} />
                <input type="password" value={password} placeholder="비밀번호" onChange={(event) => setPassword(event.target.value)} />
                <button type="submit">로그인</button>
            </form>
            {error && <p>{error}</p>}
        </main>
    )
}

export default LoginPage
