import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { getMe } from '../api/auth'
import { removeToken } from '../api/token'

// 개인정보 고지에 동의한 회원만 안쪽 화면을 보게 합니다. 동의 전이면 고지 화면(/privacy)으로 보냅니다(X-2).
// App.jsx에서 RequireLogin 안쪽, 다른 화면들의 바깥 Route로 둡니다. 순서: 로그인 확인 → 동의 확인 → 틀(AppLayout) → 화면
// 내 정보를 불러오고 state를 가지므로 components가 아니라 pages에 둡니다(데이터와 state는 page가 갖습니다).
// 이 Route는 안쪽 화면을 옮겨 다녀도 다시 만들어지지 않아서, 내 정보는 들어올 때 한 번만 불러옵니다.
// 고지 화면에서 동의하고 돌아오면 바깥에서 새로 들어오는 것이라 다시 불러옵니다.
function RequirePrivacyConsent() {
    const location = useLocation()
    const navigate = useNavigate()
    const [me, setMe] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        getMe()
            .then((data) => setMe(data))
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    // 내 정보를 받지 못하면 동의했는지 알 수 없으므로 안쪽 화면을 열지 않습니다. 다시 로그인하게 합니다.
    function handleLoginAgain() {
        removeToken()
        navigate('/login')
    }

    if (loading) {
        return (
            <main className="join-page">
                {/* 불러오는 중이라는 표시는 progress 요소로 둡니다. 값(value)이 없으면 끝을 모르는 진행 막대가 됩니다. */}
                <progress aria-label="회원 정보를 불러오는 중" />
            </main>
        )
    }

    if (error) {
        return (
            <main className="join-page">
                <section className="card join-box" role="alert">
                    <h1>회원 정보를 불러오지 못했습니다</h1>
                    <p className="err">{error}</p>
                    <div className="row">
                        <button type="button" className="btn" onClick={handleLoginAgain}>다시 로그인</button>
                    </div>
                </section>
            </main>
        )
    }

    if (me.privacyConsentedAt === null) {
        // replace: 뒤로 가기를 눌러도 동의 전에 막힌 주소로 돌아가지 않게 합니다.
        // state.from: 원래 가려던 주소입니다. 동의한 뒤 PrivacyNoticePage가 이 주소로 돌려보냅니다(예: 초대 링크).
        return <Navigate to="/privacy" replace state={{ from: location.pathname }} />
    }
    return <Outlet />
}

export default RequirePrivacyConsent
