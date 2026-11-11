import { useEffect, useRef, useState } from 'react'
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
    // 초대 링크 복사 결과 문구입니다. 비어 있으면 아무것도 보이지 않습니다.
    const [copyMessage, setCopyMessage] = useState('')
    // 복사 문구를 지우는 timer와 마지막으로 보인 문구입니다. 바뀌어도 화면을 다시 그릴 필요가 없어서 state가 아니라 ref에 둡니다.
    const copyTimerRef = useRef(null)
    const lastCopyMessageRef = useRef('')
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

    // 화면을 떠나면 아직 남은 복사 문구 timer를 지웁니다. 떠난 화면의 state를 바꾸지 않게 하기 위해서입니다.
    useEffect(() => {
        return () => clearTimeout(copyTimerRef.current)
    }, [])

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
        // 다시 발급하면 이전 링크를 복사했다는 문구는 더 맞지 않아서 지웁니다.
        clearTimeout(copyTimerRef.current)
        setCopyMessage('')
        setIssuing(true)
        issueInviteCode(teamId)
            .then((data) => setInviteCode(data.inviteCode))
            .catch((err) => setActionError(err.message))
            .finally(() => setIssuing(false))
    }

    // 초대 링크를 클립보드에 복사합니다. 성공 문구는 2초 뒤에 지우고, 실패 문구는 직접 복사할 때까지 남깁니다.
    // navigator.clipboard는 보안 context(https, localhost)에만 있습니다. 없을 때 나는 오류도 .catch로 받으려고 .then 안에서 부릅니다.
    function handleCopyInviteLink(inviteLink) {
        Promise.resolve()
            .then(() => navigator.clipboard.writeText(inviteLink))
            .then(() => showCopyMessage('링크를 복사했습니다.', true))
            // 브라우저가 권한을 거부했거나 보안 context가 아니면 여기로 옵니다.
            .catch(() => showCopyMessage('복사하지 못했습니다. 위의 링크를 직접 선택해 복사하세요.', false))
    }

    // 복사 결과 문구를 보여 줍니다. 버튼을 다시 누르면 이전 timer를 지우고 새로 셉니다.
    function showCopyMessage(message, clearLater) {
        clearTimeout(copyTimerRef.current)
        // 화면 읽기 프로그램(VoiceOver)은 직전과 같은 문구를 다시 읽지 않아서, 같으면 읽히지 않는 공백(줄바꿈 없는 공백)을 붙여 다르게 만듭니다.
        const text = message === lastCopyMessageRef.current ? message + '\u00A0' : message
        lastCopyMessageRef.current = text
        setCopyMessage(text)
        if (clearLater) {
            copyTimerRef.current = setTimeout(() => setCopyMessage(''), 2000)
        }
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
                    {isLeader && isRecording && <InviteLinkPanel inviteCode={inviteCode} onIssue={handleIssueInviteCode} issuing={issuing} onCopy={handleCopyInviteLink} copyMessage={copyMessage} />}
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
