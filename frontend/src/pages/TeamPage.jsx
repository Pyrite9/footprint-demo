import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { assignRole, getInviteCode, getTeam, getTeamMembers, issueInviteCode } from '../api/teams'
import InviteLinkPanel from '../components/InviteLinkPanel'
import TeamMemberList from '../components/TeamMemberList'

// 조 화면입니다. 조원 목록을 보여 주고, 조장에게는 초대 링크 발급과 역할 지정을 보여 줍니다.
// 이 조의 조원과 그 과제를 만든 교수만 볼 수 있습니다(서버가 확인합니다).
function TeamPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [inviteCode, setInviteCode] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    // 발급처럼 화면을 연 뒤에 하는 일의 오류입니다. 불러오기 오류(error)와 나눠서, 실패해도 화면은 그대로 둡니다.
    const [actionError, setActionError] = useState(null)
    const [issuing, setIssuing] = useState(false)
    // 조원마다 입력 중인 역할명입니다. { 조 참여 번호: 역할명 } 모양입니다.
    const [roleNameInputs, setRoleNameInputs] = useState({})
    // 지금 역할을 지정하는 중인 조 참여 번호입니다. 그 줄의 버튼만 막습니다.
    const [assigningId, setAssigningId] = useState(null)

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId)])
            .then(([meData, teamData, membersData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                // 초대 코드는 조장만 볼 수 있어서, 내가 조장일 때만 이어서 불러옵니다.
                const myTeamMember = membersData.find((member) => member.memberId === meData.id)
                if (myTeamMember && myTeamMember.leader) {
                    return getInviteCode(teamId).then((data) => setInviteCode(data.inviteCode))
                }
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    // 초대 코드를 새로 발급합니다.
    function handleIssueInviteCode() {
        setActionError(null)
        setIssuing(true)
        issueInviteCode(teamId)
            .then((data) => setInviteCode(data.inviteCode))
            .catch((err) => setActionError(err.message))
            .finally(() => setIssuing(false))
    }

    // 한 조원의 역할명 입력값을 바꿉니다. 객체 state도 직접 고치지 않고 새 객체로 바꿔 끼웁니다.
    function handleRoleNameChange(teamMemberId, value) {
        setRoleNameInputs({ ...roleNameInputs, [teamMemberId]: value })
    }

    // 한 조원에게 역할을 지정합니다. 성공하면 조원 목록에서 그 조원만 서버가 돌려준 값으로 바꿉니다.
    function handleAssignRole(teamMemberId) {
        setActionError(null)
        setAssigningId(teamMemberId)
        assignRole(teamId, teamMemberId, roleNameInputs[teamMemberId] ?? '')
            .then((updated) => {
                setMembers(members.map((member) => (member.id === updated.id ? updated : member)))
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setAssigningId(null))
    }

    if (loading) return <p>불러오는 중...</p>
    if (error) {
        return (
            <main>
                <p>{error}</p>
                <p><Link to="/">과제 목록으로</Link></p>
            </main>
        )
    }

    // 내가 이 조의 조장인지는 내 정보와 조원 목록으로 계산합니다. 계산할 수 있는 값이라 state로 두지 않습니다.
    const isLeader = members.some((member) => member.memberId === me.id && member.leader)

    return (
        <main>
            <h1>{team.name}</h1>
            <p><Link to={`/assignments/${team.assignmentId}`}>{team.assignmentTitle}</Link></p>

            <h2>조원</h2>
            <TeamMemberList
                members={members}
                canAssignRole={isLeader}
                roleNameInputs={roleNameInputs}
                onRoleNameChange={handleRoleNameChange}
                onAssignRole={handleAssignRole}
                assigningId={assigningId}
            />

            {isLeader && <InviteLinkPanel inviteCode={inviteCode} onIssue={handleIssueInviteCode} issuing={issuing} />}
            {actionError && <p>{actionError}</p>}
        </main>
    )
}

export default TeamPage
