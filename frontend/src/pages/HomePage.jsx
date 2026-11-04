import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getAssignments } from '../api/assignments'
import { getMe } from '../api/auth'
import AssignmentList from '../components/AssignmentList'
import Icon from '../components/Icon'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import TopBar from '../components/TopBar'

// 로그인 후 첫 화면입니다. 내가 볼 수 있는 과제 목록을 보여 줍니다. 로그인한 사람과 로그아웃은 왼쪽 메뉴(AppLayout)에 있습니다.
function HomePage() {
    const [me, setMe] = useState(null)
    const [assignments, setAssignments] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        // 두 요청을 함께 보내고 둘 다 끝나면 화면에 넣습니다. 하나라도 실패하면 .catch로 갑니다.
        // 내 정보는 교수인지(새 과제 버튼) 알려고 불러옵니다.
        Promise.all([getMe(), getAssignments()])
            .then(([meData, assignmentsData]) => {
                setMe(meData)
                setAssignments(assignmentsData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    return (
        <>
            <TopBar crumbs={['내 과제']} />
            <main className="content">
                <div className="page-head">
                    <h1>내 과제</h1>
                    {/* 과제는 교수만 만듭니다. 버튼을 숨기는 것은 화면 편의이고, 실제로 막는 것은 서버의 403입니다. */}
                    {me.memberType === 'PROFESSOR' && <Link to="/assignments/new" className="btn primary"><Icon name="plus" />새 과제</Link>}
                </div>
                <AssignmentList assignments={assignments} isProfessor={me.memberType === 'PROFESSOR'} />
            </main>
        </>
    )
}

export default HomePage
