import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getAssignment, startEvaluation } from '../api/assignments'
import { getMe } from '../api/auth'
import StartEvaluationPanel from '../components/StartEvaluationPanel'
import TeamSummaryList from '../components/TeamSummaryList'
import { ASSIGNMENT_PHASE_LABELS } from '../constants/assignmentPhases'

// 과제 하나의 정보와 조 목록을 보여 줍니다. 교수는 조 전부, 학생은 자기 조만 보입니다(서버가 정합니다).
// 기록 단계이면 교수에게 평가 시작 버튼을 보여 줍니다.
function AssignmentDetailPage() {
    // useParams는 주소의 :assignmentId 자리 값을 꺼냅니다. App.jsx의 path="/assignments/:assignmentId"와 이름이 같아야 합니다.
    const { assignmentId } = useParams()
    const [me, setMe] = useState(null)
    const [assignment, setAssignment] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    const [starting, setStarting] = useState(false)

    // 주소의 assignmentId가 바뀌면 다시 불러옵니다.
    useEffect(() => {
        Promise.all([getMe(), getAssignment(assignmentId)])
            .then(([meData, assignmentData]) => {
                setMe(meData)
                setAssignment(assignmentData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [assignmentId])

    // 되돌릴 수 없는 동작이라 한 번 더 묻고 평가를 시작합니다. 응답에 조 목록이 없어서 단계와 시각만 바꿔 끼웁니다.
    function handleStartEvaluation() {
        if (!window.confirm('평가를 시작할까요? 기록 단계로 되돌릴 수 없습니다.')) return
        setActionError(null)
        setStarting(true)
        startEvaluation(assignmentId)
            .then((updated) => {
                setAssignment({ ...assignment, phase: updated.phase, evaluationStartedAt: updated.evaluationStartedAt })
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setStarting(false))
    }

    if (loading) return <main><p>불러오는 중...</p></main>
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
            <p>
                <span className="badge">{ASSIGNMENT_PHASE_LABELS[assignment.phase]}</span>
                {assignment.evaluationStartedAt && <span className="muted"> 평가 시작 {assignment.evaluationStartedAt.replace('T', ' ')}</span>}
            </p>
            <h2>조</h2>
            <TeamSummaryList teams={assignment.teams} />
            {/* 이 화면을 볼 수 있는 교수는 과제를 만든 교수뿐입니다(서버가 확인합니다). */}
            {me.memberType === 'PROFESSOR' && assignment.phase === 'RECORDING' && (
                <StartEvaluationPanel onStart={handleStartEvaluation} starting={starting} />
            )}
            {actionError && <p className="error">{actionError}</p>}
        </main>
    )
}

export default AssignmentDetailPage
