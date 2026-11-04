import { useEffect, useState } from 'react'
import { Navigate, useOutletContext, useParams } from 'react-router'
import { getAssignment, startEvaluation } from '../api/assignments'
import { getMe } from '../api/auth'
import ConfirmBox from '../components/ConfirmBox'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import TeamSummaryList from '../components/TeamSummaryList'
import TopBar from '../components/TopBar'

// 과제의 조 현황입니다. 교수는 조 전부를 보고, 기록 단계이면 평가를 시작합니다.
// 학생은 자기 조만 볼 수 있어서(서버가 정합니다), 자기 조 화면으로 바로 보냅니다.
function AssignmentDetailPage() {
    // useParams는 주소의 :assignmentId 자리 값을 꺼냅니다. App.jsx의 path="/assignments/:assignmentId"와 이름이 같아야 합니다.
    const { assignmentId } = useParams()
    const { reloadLayout } = useOutletContext()
    const [me, setMe] = useState(null)
    const [assignment, setAssignment] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    // 평가 시작 확인 상자를 열어 두었으면 true입니다.
    const [confirming, setConfirming] = useState(false)
    const [starting, setStarting] = useState(false)

    useEffect(() => {
        Promise.all([getMe(), getAssignment(assignmentId)])
            .then(([meData, assignmentData]) => {
                setMe(meData)
                setAssignment(assignmentData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [assignmentId])

    // 평가를 시작합니다. 응답에 조 목록이 없어서 단계와 시각만 바꿔 끼웁니다. 왼쪽 메뉴의 단계도 바뀌어야 해서 틀도 다시 불러옵니다.
    function handleStartEvaluation() {
        setActionError(null)
        setStarting(true)
        startEvaluation(assignmentId)
            .then((updated) => {
                setAssignment({ ...assignment, phase: updated.phase, evaluationStartedAt: updated.evaluationStartedAt })
                setConfirming(false)
                reloadLayout()
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setStarting(false))
    }

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    const isProfessor = me.memberType === 'PROFESSOR'
    // replace: 뒤로 가기를 눌렀을 때 이 주소로 돌아와 다시 넘어가는 일을 막습니다.
    if (!isProfessor && assignment.teams.length === 1) {
        return <Navigate to={`/teams/${assignment.teams[0].id}`} replace />
    }

    return (
        <>
            <TopBar crumbs={[assignment.title, '조 현황']} />
            {actionError && <div className="errband" role="alert">{actionError}</div>}
            <main className="content">
                {confirming && (
                    <ConfirmBox
                        title="평가를 시작할까요?"
                        body="조원이 동료평가를 입력할 수 있게 됩니다. 초대, 역할 지정·변경, 조장 교체, 산출물 올리기·삭제, 기여 항목 등록·보고·취소, 완료확인은 더 할 수 없고, 기록 단계로 되돌릴 수 없습니다."
                        okLabel="평가 시작"
                        okClass="primary"
                        onOk={handleStartEvaluation}
                        onCancel={() => setConfirming(false)}
                        busy={starting}
                    />
                )}
                <div className="page-head">
                    <div>
                        <h1>조 현황</h1>
                        <p>{assignment.title} · {assignment.startDate} ~ {assignment.endDate}</p>
                    </div>
                    {/* 이 화면을 볼 수 있는 교수는 과제를 만든 교수뿐입니다(서버가 확인합니다). */}
                    {isProfessor && assignment.phase === 'RECORDING' && !confirming && (
                        <button type="button" className="btn" onClick={() => setConfirming(true)}>평가 시작</button>
                    )}
                </div>
                <TeamSummaryList teams={assignment.teams} />
            </main>
        </>
    )
}

export default AssignmentDetailPage
