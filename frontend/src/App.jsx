import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import RequireLogin from './components/RequireLogin'
import AssignmentCreatePage from './pages/AssignmentCreatePage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'

// 화면 전체의 시작점입니다. 주소마다 어떤 화면을 보여 줄지 정합니다.
function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<LoginPage />} />

                {/* 이 안쪽 주소는 로그인해야 볼 수 있습니다. 새 화면은 이 안에 Route를 한 줄 추가합니다. */}
                <Route element={<RequireLogin />}>
                    <Route path="/" element={<HomePage />} />
                    <Route path="/assignments/new" element={<AssignmentCreatePage />} />
                </Route>

                {/* 없는 주소는 첫 화면으로 보냅니다. */}
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </BrowserRouter>
    )
}

export default App
