import { useEffect, useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router'
import { getMe } from '../api/auth'
import { removeToken } from '../api/token'
import ThemeToggle from '../components/ThemeToggle'
import { MEMBER_TYPE_LABELS } from '../constants/memberTypes'
import { getCurrentTheme, saveTheme } from '../theme'

// 로그인한 뒤 모든 화면 위에 붙는 공통 틀입니다. 상단 바(로고, 로그인한 사람, 테마 버튼, 로그아웃) 아래에 각 화면을 그립니다.
// App.jsx에서 화면들의 바깥 Route로 둡니다. <Outlet />이 그 안쪽 화면이 들어갈 자리입니다.
// 내 정보를 불러오고 테마 state를 가지므로 pages에 둡니다(데이터와 state는 page가 갖습니다).
function AppLayout() {
    const [me, setMe] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    // 지금 테마('light' 또는 'dark')입니다. 고른 테마는 theme.js가 브라우저에 기억합니다.
    const [theme, setTheme] = useState(getCurrentTheme())
    const navigate = useNavigate()

    // 상단 바는 화면을 옮겨도 다시 그려지지 않아서, 내 정보는 로그인 뒤 한 번만 불러옵니다.
    useEffect(() => {
        getMe()
            .then((data) => setMe(data))
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    // 테마를 반대로 바꾸고 기억합니다.
    function handleThemeToggle() {
        const next = theme === 'dark' ? 'light' : 'dark'
        saveTheme(next)
        setTheme(next)
    }

    // token을 지우고 로그인 화면으로 갑니다. 서버는 token을 기억하지 않으므로 서버에 알릴 것이 없습니다.
    function handleLogout() {
        removeToken()
        navigate('/login')
    }

    return (
        <>
            <header className="app-header">
                <Link to="/" className="logo">발자국</Link>
                <div className="end">
                    {/* 불러오는 중이거나 실패해도 상단 바와 로그아웃은 보여 줍니다. 실패 이유는 아래 화면이 다시 알려 줍니다. */}
                    {loading && <span className="muted">불러오는 중...</span>}
                    {error && <span className="muted">내 정보를 불러오지 못했습니다</span>}
                    {me && <span>{me.name} <span className="muted">({MEMBER_TYPE_LABELS[me.memberType]})</span></span>}
                    <ThemeToggle theme={theme} onToggle={handleThemeToggle} />
                    <button type="button" className="secondary" onClick={handleLogout}>로그아웃</button>
                </div>
            </header>
            <Outlet />
        </>
    )
}

export default AppLayout
