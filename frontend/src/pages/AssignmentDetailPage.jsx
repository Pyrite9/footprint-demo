import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getAssignment } from '../api/assignments'
import TeamSummaryList from '../components/TeamSummaryList'

// 과제 하나의 정보와 조 목록을 보여 줍니다. 교수는 조 전부, 학생은 자기 조만 보입니다(서버가 정합니다).
function AssignmentDetailPage() {
    // useParams는 주소의 :assignmentId 자리 값을 꺼냅니다. App.jsx의 path="/assignments/:assignmentId"와 이름이 같아야 합니다.
    const { assignmentId } = useParams()
    const [assignment, setAssignment] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    // 주소의 assignmentId가 바뀌면 다시 불러옵니다.
    useEffect(() => {
        getAssignment(assignmentId)
            .then((data) => setAssignment(data))
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [assignmentId])

    if (loading) return <p>불러오는 중...</p>
    if (error) {
        return (
            <main>
                <p className="error">{error}</p>
                <p><Link to="/">과제 목록으로</Link></p>
            </main>
        )
    }

    return (
        <main>
            <p className="links"><Link to="/">← 과제 목록</Link></p>
            <h1>{assignment.title}</h1>
            <p className="muted">기간 {assignment.startDate} ~ {assignment.endDate} · 담당 교수 {assignment.professorName}</p>
            <h2>조</h2>
            <TeamSummaryList teams={assignment.teams} />
        </main>
    )
}

export default AssignmentDetailPage
