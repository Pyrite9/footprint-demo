import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { createContribution, getContributions } from '../api/contributions'
import { getTeam, getTeamMembers } from '../api/teams'
import ContributionCreateForm from '../components/ContributionCreateForm'
import ContributionList from '../components/ContributionList'

// 조의 기여 항목 화면입니다. 목록은 조원과 교수가 보고, 등록은 기록 단계에서 조장만 합니다(서버가 확인합니다).
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
                    creating={creating}
                />
            )}
            {!isRecording && <p className="notice">평가 단계입니다. 기여 항목을 더 등록할 수 없습니다.</p>}
            {actionError && <p className="error">{actionError}</p>}

            <h2>기여 항목 (최근 등록순)</h2>
            <ContributionList contributions={contributions} />
        </main>
    )
}

export default ContributionsPage
