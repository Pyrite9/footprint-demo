import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { getAssignments } from '../api/assignments'
import { getMe } from '../api/auth'
import { removeToken } from '../api/token'
import AssignmentList from '../components/AssignmentList'
import { MEMBER_TYPE_LABELS } from '../constants/memberTypes'

// 로그인 후 첫 화면입니다. 내 정보와 내가 볼 수 있는 과제 목록을 보여 주고 로그아웃할 수 있습니다.
function HomePage() {
    const [me, setMe] = useState(null)
    const [assignments, setAssignments] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const navigate = useNavigate()

    useEffect(() => {
        // 두 요청을 함께 보내고 둘 다 끝나면 화면에 넣습니다. 하나라도 실패하면 .catch로 갑니다.
        Promise.all([getMe(), getAssignments()])
            .then(([meData, assignmentsData]) => {
                setMe(meData)
                setAssignments(assignmentsData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    // token을 지우고 로그인 화면으로 갑니다. 서버는 token을 기억하지 않으므로 서버에 알릴 것이 없습니다.
    function handleLogout() {
        removeToken()
        navigate('/login')
    }

    if (loading) return <p>불러오는 중...</p>

    // 서버가 꺼져 있는 등 정보를 못 불러와도 로그아웃은 할 수 있게 버튼을 함께 둡니다.
    if (error) {
        return (
            <main>
                <p className="error">{error}</p>
                <button type="button" className="secondary" onClick={handleLogout}>로그아웃</button>
            </main>
        )
    }

    return (
        <main>
            <h1>발자국</h1>
            <div className="row">
                <span>{me.name} ({MEMBER_TYPE_LABELS[me.memberType]})님, 환영합니다.</span>
                <button type="button" className="secondary" onClick={handleLogout}>로그아웃</button>
            </div>

            <h2>과제 목록</h2>
            {/* 과제는 교수만 만듭니다. 버튼을 숨기는 것은 화면 편의이고, 실제로 막는 것은 서버의 403입니다. */}
            {me.memberType === 'PROFESSOR' && <p><Link to="/assignments/new">+ 과제 만들기</Link></p>}
            <AssignmentList assignments={assignments} />
        </main>
    )
}

export default HomePage
