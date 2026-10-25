import { Navigate, Outlet, useLocation } from 'react-router'
import { getToken } from '../api/token'

// 로그인한 사용자만 안쪽 화면을 보게 합니다. token이 없으면 로그인 화면으로 보냅니다.
// <Outlet />은 App.jsx에서 이 Route 안쪽에 적은 화면이 들어갈 자리입니다.
function RequireLogin() {
    const location = useLocation()

    if (!getToken()) {
        // replace: 뒤로 가기를 눌러도 막힌 주소로 돌아가지 않도록 방문 기록을 바꿔 끼웁니다.
        // state.from: 원래 가려던 주소입니다. 로그인한 뒤 LoginPage가 이 주소로 돌려보냅니다(예: 초대 링크).
        return <Navigate to="/login" replace state={{ from: location.pathname }} />
    }
    return <Outlet />
}

export default RequireLogin
