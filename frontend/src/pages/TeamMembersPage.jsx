import { useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { assignRole, changeLeader, changeRole, getInviteCode, getLeaderHistories, getRoleHistories, getTeam, getTeamMembers, issueInviteCode } from '../api/teams'
import ConfirmBox from '../components/ConfirmBox'
import DetailPanel from '../components/DetailPanel'
import InviteLinkPanel from '../components/InviteLinkPanel'
import LeaderHistoryPanel from '../components/LeaderHistoryPanel'
import MemberDetail from '../components/MemberDetail'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import TeamMemberList from '../components/TeamMemberList'
import TopBar from '../components/TopBar'

// 조원과 역할 화면입니다. 조원 목록과 역할·조장 이력을 보여 주고, 기록 단계이면 조장에게 초대 링크 발급과 역할 지정·변경을, 교수에게 조장 교체를 보여 줍니다.
// 이 조의 조원과 그 과제를 만든 교수만 볼 수 있습니다(서버가 확인합니다).
function TeamMembersPage() {
    const { teamId } = useParams()
    const location = useLocation()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [inviteCode, setInviteCode] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    // 발급처럼 화면을 연 뒤에 하는 일의 오류입니다. 불러오기 오류(error)와 나눠서, 실패해도 화면은 그대로 둡니다.
    const [actionError, setActionError] = useState(null)
    const [issuing, setIssuing] = useState(false)
    // 오른쪽 상세에 연 조원의 조 참여 번호입니다. 조 홈의 "역할 지정"에서 왔으면 그 조원을 바로 엽니다.
    const [selectedId, setSelectedId] = useState(location.state?.selectedId ?? null)
    const [roleHistories, setRoleHistories] = useState([])
    const [roleNameInput, setRoleNameInput] = useState('')
    const [savingRole, setSavingRole] = useState(false)
    const [leaderHistories, setLeaderHistories] = useState([])
    // 조장 교체 확인 상자를 열어 두었으면 true입니다.
    const [confirmingLeader, setConfirmingLeader] = useState(false)
    const [changingLeader, setChangingLeader] = useState(false)

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getLeaderHistories(teamId)])
            .then(([meData, teamData, membersData, leaderHistoriesData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setLeaderHistories(leaderHistoriesData)
                // 초대 코드는 조장만 볼 수 있어서, 내가 조장일 때만 이어서 불러옵니다.
                const myTeamMember = membersData.find((member) => member.memberId === meData.id)
                if (myTeamMember && myTeamMember.leader) {
                    return getInviteCode(teamId).then((data) => setInviteCode(data.inviteCode))
                }
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    // 조원을 열면 그 조원의 역할 이력을 불러옵니다.
    useEffect(() => {
        if (selectedId === null) return undefined
        // 다른 조원을 빠르게 누르면 늦게 온 옛 응답이 이력을 덮어쓸 수 있습니다. 조원이 바뀌면 이전 요청의 결과를 버립니다.
        let ignore = false
        getRoleHistories(teamId, selectedId)
            .then((data) => {
                if (!ignore) setRoleHistories(data)
            })
            .catch((err) => {
                if (!ignore) setActionError(err.message)
            })
        return () => {
            ignore = true
        }
    }, [teamId, selectedId])

    // 조원을 오른쪽 상세에 엽니다. 이전 조원의 입력값과 이력은 지웁니다.
    function handleSelect(teamMemberId) {
        setActionError(null)
        setSelectedId(teamMemberId)
        setRoleHistories([])
        setRoleNameInput('')
        setConfirmingLeader(false)
    }

    // 초대 코드를 새로 발급합니다.
    function handleIssueInviteCode() {
        setActionError(null)
        setIssuing(true)
        issueInviteCode(teamId)
            .then((data) => setInviteCode(data.inviteCode))
            .catch((err) => setActionError(err.message))
            .finally(() => setIssuing(false))
    }

    // 조원의 역할을 저장합니다. 역할이 없으면 지정(POST), 있으면 변경(PUT)입니다.
    // 성공하면 조원 목록에서 그 조원만 서버가 돌려준 값으로 바꾸고, 방금 생긴 줄이 보이도록 이력을 다시 불러옵니다.
    function handleSaveRole(target) {
        const save = target.roleName === null ? assignRole : changeRole
        setActionError(null)
        setSavingRole(true)
        save(teamId, target.id, roleNameInput)
            .then((updated) => {
                setMembers(members.map((member) => (member.id === updated.id ? updated : member)))
                setRoleNameInput('')
                return getRoleHistories(teamId, updated.id).then((data) => setRoleHistories(data))
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setSavingRole(false))
    }

    // 조장을 바꿉니다. 응답은 새 조장 한 줄이라, 조원 목록의 조장 표시를 그 번호로 다시 정하고 이력을 새로 불러옵니다.
    function handleChangeLeader(target) {
        setActionError(null)
        setChangingLeader(true)
        changeLeader(teamId, target.id)
            .then((updated) => {
                setMembers(members.map((member) => ({ ...member, leader: member.id === updated.id })))
                setConfirmingLeader(false)
                return getLeaderHistories(teamId).then((data) => setLeaderHistories(data))
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setChangingLeader(false))
    }

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    // 내가 이 조의 조장인지는 내 정보와 조원 목록으로 계산합니다. 계산할 수 있는 값이라 state로 두지 않습니다.
    const isLeader = members.some((member) => member.memberId === me.id && member.leader)
    // 평가 단계에서는 조 운영을 바꿀 수 없습니다.
    const isRecording = team.phase === 'RECORDING'
    // 이 화면을 볼 수 있는 교수는 과제를 만든 교수뿐입니다(서버가 확인합니다).
    const isProfessor = me.memberType === 'PROFESSOR'
    const canAssignRole = isLeader && isRecording
    const canChangeLeader = isProfessor && isRecording
    const selectedMember = members.find((member) => member.id === selectedId)

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, isProfessor ? '조원과 조장' : '조원과 역할']} />
            {!isRecording && <div className="lockband">평가 단계입니다. 초대, 역할 지정·변경, 조장 교체는 더 할 수 없습니다.</div>}
            {actionError && <div className="errband" role="alert">{actionError}</div>}
            <div className={selectedMember ? 'main-row detail-open' : 'main-row'}>
                <main className="content">
                    <div className="page-head">
                        <h1>{isProfessor ? `${team.name} 조원과 조장` : '조원과 역할'}</h1>
                    </div>
                    {isLeader && isRecording && <InviteLinkPanel inviteCode={inviteCode} onIssue={handleIssueInviteCode} issuing={issuing} />}
                    <TeamMemberList
                        members={members}
                        selectedId={selectedId}
                        onSelect={handleSelect}
                        canAssignRole={canAssignRole}
                        canChangeLeader={canChangeLeader}
                    />
                    {leaderHistories.length > 0 && <LeaderHistoryPanel histories={leaderHistories} />}
                </main>
                {selectedMember && (
                    <DetailPanel title={selectedMember.name} onClose={() => setSelectedId(null)}>
                        {confirmingLeader && (
                            <ConfirmBox
                                title={`${selectedMember.name} 조원을 새 조장으로 바꿀까요?`}
                                body="교체 이력이 남습니다. 이전 조장은 일반 조원으로 남고 역할과 기록은 그대로입니다."
                                okLabel="새 조장으로 지정"
                                okClass="primary"
                                onOk={() => handleChangeLeader(selectedMember)}
                                onCancel={() => setConfirmingLeader(false)}
                                busy={changingLeader}
                            />
                        )}
                        <MemberDetail
                            member={selectedMember}
                            histories={roleHistories}
                            canAssignRole={canAssignRole}
                            canChangeLeader={canChangeLeader}
                            roleNameInput={roleNameInput}
                            onRoleNameChange={setRoleNameInput}
                            onSaveRole={() => handleSaveRole(selectedMember)}
                            onChangeLeader={() => setConfirmingLeader(true)}
                            busy={savingRole || changingLeader}
                        />
                    </DetailPanel>
                )}
            </div>
        </>
    )
}

export default TeamMembersPage
