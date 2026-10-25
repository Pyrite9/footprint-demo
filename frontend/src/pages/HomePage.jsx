import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { getMe } from '../api/auth'
import { removeToken } from '../api/token'
import { MEMBER_TYPE_LABELS } from '../constants/memberTypes'

// 로그인 후 첫 화면입니다. 내 정보를 보여 주고 로그아웃할 수 있습니다. 과제 목록(A-2-1)이 생기면 바뀝니다.
function HomePage() {
    const [me, setMe] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const navigate = useNavigate()

    useEffect(() => {
        getMe()
            .then((data) => setMe(data))
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    // token을 지우고 로그인 화면으로 갑니다. 서버는 token을 기억하지 않으므로 서버에 알릴 것이 없습니다.
    function handleLogout() {
        removeToken()
        navigate('/login')
    }

    if (loading) return <p>불러오는 중...</p>

    // 서버가 꺼져 있는 등 내 정보를 못 불러와도 로그아웃은 할 수 있게 버튼을 함께 둡니다.
    if (error) {
        return (
            <main>
                <p>{error}</p>
                <button type="button" onClick={handleLogout}>로그아웃</button>
            </main>
        )
    }

    return (
        <main>
            <h1>발자국</h1>
            <p>{me.name} ({MEMBER_TYPE_LABELS[me.memberType]})님, 환영합니다.</p>
            <button type="button" onClick={handleLogout}>로그아웃</button>
        </main>
    )
}

export default HomePage
