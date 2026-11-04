import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { createContribution, getContributions, reportContribution } from '../api/contributions'
import { getTeam, getTeamMembers } from '../api/teams'
import ContributionCreateForm from '../components/ContributionCreateForm'
import ContributionList from '../components/ContributionList'

// 조의 기여 항목 화면입니다. 목록은 조원과 교수가 보고, 기록 단계에서 등록은 조장이, 완료 보고는 담당 조원이 합니다(서버가 확인합니다).
function ContributionsPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [contributions, setContributions] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    const [title, setTitle] = useState('')
    // 고른 담당 조원의 조 참여 번호입니다. select의 값은 글자라서 보낼 때 숫자로 바꿉니다.
    const [assigneeId, setAssigneeId] = useState('')
    const [creating, setCreating] = useState(false)
    // 완료 보고 form을 열어 둔 항목의 번호입니다. 한 번에 하나만 열립니다.
    const [reportTargetId, setReportTargetId] = useState(null)
    const [memo, setMemo] = useState('')
    // 등록과 보고는 서로 끝날 때까지 막습니다. 동시에 끝나면 나중에 끝난 쪽이 옛 contributions로 덮어써 먼저 끝난 결과가 사라지기 때문입니다.
    const [reporting, setReporting] = useState(false)

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getContributions(teamId)])
            .then(([meData, teamData, membersData, contributionsData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setContributions(contributionsData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    // 기여 항목을 등록합니다. 성공하면 새 항목을 목록 맨 위에 더하고 입력 칸을 비웁니다.
    function handleCreate() {
        if (title.trim() === '') {
            setActionError('항목명을 쓰세요')
            return
        }
        if (assigneeId === '') {
            setActionError('담당 조원을 고르세요')
            return
        }
        setActionError(null)
        setCreating(true)
        createContribution(teamId, title, Number(assigneeId))
            .then((created) => {
                setContributions([created, ...contributions])
                setTitle('')
                setAssigneeId('')
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setCreating(false))
    }

    // 완료 보고 form을 엽니다. 다른 항목의 form이 열려 있었으면 쓰던 메모는 지웁니다.
    function handleStartReport(target) {
        setActionError(null)
        setReportTargetId(target.id)
        setMemo('')
    }

    // 완료 보고 form을 닫습니다.
    function handleCloseReport() {
        setReportTargetId(null)
        setMemo('')
    }

    // 완료 보고합니다. 성공하면 그 줄만 서버가 돌려준 값으로 바꾸고 form을 닫습니다.
    function handleReport() {
        if (memo.trim() === '') {
            setActionError('완료 보고 메모를 쓰세요')
            return
        }
        setActionError(null)
        setReporting(true)
        reportContribution(reportTargetId, memo)
            .then((updated) => {
                setContributions(contributions.map((contribution) => (contribution.id === updated.id ? updated : contribution)))
                handleCloseReport()
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setReporting(false))
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

    // 내 조 참여 기록입니다. 교수는 조원이 아니라서 없습니다(undefined).
    const myTeamMember = members.find((member) => member.memberId === me.id)
    const isRecording = team.phase === 'RECORDING'
    const canCreate = isRecording && myTeamMember !== undefined && myTeamMember.leader

    return (
        <main>
            <p className="links"><Link to={`/teams/${team.id}`}>← {team.name}</Link></p>
            <h1>{team.name} 기여 항목</h1>

            {canCreate && (
                <ContributionCreateForm
                    title={title}
                    assigneeId={assigneeId}
                    members={members}
                    onTitleChange={setTitle}
                    onAssigneeChange={setAssigneeId}
                    onSubmit={handleCreate}
                    creating={creating || reporting}
                />
            )}
            {!isRecording && <p className="notice">평가 단계입니다. 기여 항목을 더 등록하거나 보고할 수 없습니다.</p>}
            {actionError && <p className="error">{actionError}</p>}

            <h2>기여 항목 (최근 등록순)</h2>
            <ContributionList
                contributions={contributions}
                currentMemberId={me.id}
                canReport={isRecording}
                reportTargetId={reportTargetId}
                memo={memo}
                onStartReport={handleStartReport}
                onMemoChange={setMemo}
                onSubmitReport={handleReport}
                onCloseReport={handleCloseReport}
                reporting={reporting || creating}
            />
        </main>
    )
}

export default ContributionsPage
