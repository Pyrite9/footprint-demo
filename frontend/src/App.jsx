import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import RequireLogin from './components/RequireLogin'
import AssignmentCreatePage from './pages/AssignmentCreatePage'
import AssignmentDetailPage from './pages/AssignmentDetailPage'
import DeliverablesPage from './pages/DeliverablesPage'
import EvaluationPage from './pages/EvaluationPage'
import HomePage from './pages/HomePage'
import JoinPage from './pages/JoinPage'
import LoginPage from './pages/LoginPage'
import TeamPage from './pages/TeamPage'

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
                    {/* :assignmentId 자리의 값은 page에서 useParams()로 꺼냅니다. React Router는 더 구체적인 /assignments/new를 먼저 고르므로 "new"가 id로 읽히지 않습니다. */}
                    <Route path="/assignments/:assignmentId" element={<AssignmentDetailPage />} />
                    <Route path="/teams/:teamId" element={<TeamPage />} />
                    <Route path="/teams/:teamId/deliverables" element={<DeliverablesPage />} />
                    <Route path="/teams/:teamId/evaluation" element={<EvaluationPage />} />
                    {/* 초대 링크입니다. 로그인하지 않았으면 로그인한 뒤 이 주소로 돌아옵니다(RequireLogin, LoginPage). */}
                    <Route path="/join/:inviteCode" element={<JoinPage />} />
                </Route>

                {/* 없는 주소는 첫 화면으로 보냅니다. */}
                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </BrowserRouter>
    )
}

export default App
