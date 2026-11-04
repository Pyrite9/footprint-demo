import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { getMe } from '../api/auth'
import { getContributions } from '../api/contributions'
import { getDeliverables } from '../api/deliverables'
import { getMyEvaluationStatus } from '../api/evaluations'
import { getInviteCode, getTeam, getTeamMembers } from '../api/teams'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import RecentDeliverableList from '../components/RecentDeliverableList'
import TeamTodoList from '../components/TeamTodoList'
import TopBar from '../components/TopBar'
import { ASSIGNMENT_PHASE_LABELS } from '../constants/assignmentPhases'
import { initialOf, PHASE_PILLS, roleColorClass } from '../format'

// 조 홈입니다. 내가 지금 할 수 있는 일과 최근 산출물을 보여 줍니다. 이 조의 조원과 그 과제를 만든 교수만 볼 수 있습니다(서버가 확인합니다).
function TeamPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [deliverables, setDeliverables] = useState([])
    const [contributions, setContributions] = useState([])
    const [inviteCode, setInviteCode] = useState(null)
    const [evaluationStatus, setEvaluationStatus] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getDeliverables(teamId), getContributions(teamId)])
            .then(([meData, teamData, membersData, deliverablesData, contributionsData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setDeliverables(deliverablesData)
                setContributions(contributionsData)
                // 초대 코드는 조장만, 평가 제출 여부는 조원만 볼 수 있어서 해당할 때만 이어서 불러옵니다. null은 부르지 않은 것입니다.
                const myTeamMember = membersData.find((member) => member.memberId === meData.id)
                const needsInvite = myTeamMember && myTeamMember.leader && teamData.phase === 'RECORDING'
                const needsStatus = myTeamMember && teamData.phase === 'EVALUATION'
                return Promise.all([needsInvite ? getInviteCode(teamId) : null, needsStatus ? getMyEvaluationStatus(teamId) : null])
            })
            .then(([inviteData, statusData]) => {
                if (inviteData) setInviteCode(inviteData.inviteCode)
                setEvaluationStatus(statusData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    // 내 조 참여 기록입니다. 교수는 조원이 아니라서 없습니다(undefined).
    const myTeamMember = members.find((member) => member.memberId === me.id)

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, '홈']} />
            <main className="content">
                <div className="page-head">
                    <div>
                        <h1>{team.name}</h1>
                        <div className="row">
                            <span className={`pill ${PHASE_PILLS[team.phase]}`}>{ASSIGNMENT_PHASE_LABELS[team.phase]}</span>
                            {myTeamMember && (
                                <span className={`role ${roleColorClass(myTeamMember.roleName)}`}>
                                    <span className="dot" />내 역할 {myTeamMember.roleName ?? '없음'}
                                </span>
                            )}
                            {myTeamMember && myTeamMember.leader && <span className="pill blue">조장</span>}
                        </div>
                    </div>
                    <div className="stack">
                        {members.map((member) => <span key={member.id} className="av" title={member.name}>{initialOf(member.name)}</span>)}
                    </div>
                </div>
                {myTeamMember && myTeamMember.roleName === null && team.phase === 'RECORDING' && (
                    <div className="vis">조장이 역할을 정하면 파일을 올릴 수 있습니다. 그전에도 조의 기록은 볼 수 있습니다.</div>
                )}
                {myTeamMember && (
                    <TeamTodoList todos={buildTodos(team, members, contributions, myTeamMember, inviteCode, evaluationStatus)} />
                )}
                <RecentDeliverableList teamId={team.id} deliverables={deliverables} />
            </main>
        </>
    )
}

// 조원이 지금 할 수 있는 일을 불러온 기록으로 만듭니다. to는 갈 주소, selectedId는 그 화면에서 바로 열 줄입니다.
function buildTodos(team, members, contributions, myTeamMember, inviteCode, evaluationStatus) {
    if (team.phase === 'EVALUATION') {
        return evaluationStatus && !evaluationStatus.submitted ? [{ text: '동료평가 입력하기', to: `/teams/${team.id}/evaluation` }] : []
    }

    const todos = []
    if (myTeamMember.leader && inviteCode === null) {
        todos.push({ text: '초대 링크 발급하기', to: `/teams/${team.id}/members` })
    }
    contributions
        .filter((contribution) => contribution.assigneeMemberId === myTeamMember.memberId && contribution.reportedAt === null && contribution.canceledAt === null)
        .forEach((contribution) => {
            todos.push({ text: `완료 보고: ${contribution.title}`, to: `/teams/${team.id}/contributions`, selectedId: contribution.id })
        })
    if (myTeamMember.leader) {
        members
            .filter((member) => member.roleName === null)
            .forEach((member) => {
                todos.push({ text: `역할 지정: ${member.name}`, to: `/teams/${team.id}/members`, selectedId: member.id })
            })
    }
    return todos
}

export default TeamPage
