import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getAssignments } from '../api/assignments'
import { getMe } from '../api/auth'
import AssignmentList from '../components/AssignmentList'

// 로그인 후 첫 화면입니다. 내가 볼 수 있는 과제 목록을 보여 줍니다. 로그인한 사람과 로그아웃은 상단 바(AppLayout)에 있습니다.
function HomePage() {
    const [me, setMe] = useState(null)
    const [assignments, setAssignments] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        // 두 요청을 함께 보내고 둘 다 끝나면 화면에 넣습니다. 하나라도 실패하면 .catch로 갑니다.
        // 내 정보는 교수인지(과제 만들기 링크) 알려고 불러옵니다.
        Promise.all([getMe(), getAssignments()])
            .then(([meData, assignmentsData]) => {
                setMe(meData)
                setAssignments(assignmentsData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    if (loading) return <main><p>불러오는 중...</p></main>
    if (error) {
        return (
            <main>
                <p className="error">{error}</p>
            </main>
        )
    }

    return (
        <main>
            <div className="row">
                <h1>과제 목록</h1>
                {/* 과제는 교수만 만듭니다. 링크를 숨기는 것은 화면 편의이고, 실제로 막는 것은 서버의 403입니다. */}
                {me.memberType === 'PROFESSOR' && <Link to="/assignments/new" className="end">+ 과제 만들기</Link>}
            </div>
            <AssignmentList assignments={assignments} />
        </main>
    )
}

export default HomePage
