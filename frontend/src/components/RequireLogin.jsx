import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { refresh } from '../api/auth'
import { getToken } from '../api/token'

// 로그인한 사용자만 안쪽 화면을 보게 합니다. <Outlet />은 App.jsx에서 이 Route 안쪽에 적은 화면이 들어갈 자리입니다.
// access token은 메모리에만 있어서 새로고침하면 사라집니다. 그래서 token이 없으면 바로 로그인 화면으로 보내지 않고
// refresh token(cookie)으로 다시 받아 봅니다. 받으면 안쪽 화면을, 받지 못하면 로그인 화면을 보여 줍니다.
// 데이터를 불러오고 state를 갖지만, 화면이 아니라 문지기 역할이라 기존 위치(components)에 둡니다.
function RequireLogin() {
    const location = useLocation()
    // 'ready': token이 있음, 'checking': 재발급 중, 'failed': 재발급 실패
    // useState에 함수를 넘기면 처음 한 번만 실행해 첫 값으로 씁니다.
    const [status, setStatus] = useState(() => (getToken() !== null ? 'ready' : 'checking'))

    useEffect(() => {
        if (status !== 'checking') {
            return
        }
        // StrictMode는 개발 중 이 effect를 두 번 실행합니다. refresh()는 진행 중인 재발급을 함께 기다리게 해서 요청은 하나만 나갑니다.
        refresh()
            .then(() => setStatus('ready'))
            .catch(() => setStatus('failed'))
    }, [status])

    if (status === 'checking') {
        return (
            <main className="join-page">
                {/* 불러오는 중이라는 표시는 progress 요소로 둡니다. 값(value)이 없으면 끝을 모르는 진행 막대가 됩니다. */}
                <progress aria-label="로그인을 확인하는 중" />
            </main>
        )
    }

    if (status === 'failed') {
        // replace: 뒤로 가기를 눌러도 막힌 주소로 돌아가지 않도록 방문 기록을 바꿔 끼웁니다.
        // state.from: 원래 가려던 주소입니다. 로그인한 뒤 LoginPage가 이 주소로 돌려보냅니다(예: 초대 링크).
        return <Navigate to="/login" replace state={{ from: location.pathname }} />
    }
    return <Outlet />
}

export default RequireLogin
