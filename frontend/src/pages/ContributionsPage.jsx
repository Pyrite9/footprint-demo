import { useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { cancelConfirmation, confirmContribution, getContributionConfirmations } from '../api/confirmations'
import { cancelContribution, createContribution, getContributions, reportContribution } from '../api/contributions'
import { getTeam, getTeamMembers } from '../api/teams'
import ConfirmBox from '../components/ConfirmBox'
import ContributionCreateForm from '../components/ContributionCreateForm'
import ContributionDetail from '../components/ContributionDetail'
import ContributionList from '../components/ContributionList'
import DetailPanel from '../components/DetailPanel'
import Icon from '../components/Icon'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import TopBar from '../components/TopBar'
import { contributionStatusOf } from '../format'

// 상태로 거르는 탭입니다. 전체를 뺀 이름은 format.js의 contributionStatusOf가 돌려주는 값과 같습니다.
const STATUS_TABS = ['전체', '보고 전', '보고됨', '취소됨']

// 조의 기여 항목 화면입니다. 목록과 완료확인 이력은 조원과 교수가 보고, 기록 단계에서 등록과 취소는 조장이, 완료 보고는 담당 조원이, 완료확인은 담당 조원을 뺀 조원이 합니다(서버가 확인합니다).
function ContributionsPage() {
    const { teamId } = useParams()
    const location = useLocation()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [contributions, setContributions] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    const [statusTab, setStatusTab] = useState('전체')
    // 오른쪽 상세에 연 항목의 번호입니다. 조 홈의 "완료 보고"에서 왔으면 그 항목을 바로 엽니다.
    const [selectedId, setSelectedId] = useState(location.state?.selectedId ?? null)
    // 오른쪽에 등록 form을 열어 두었으면 true입니다. 상세와 등록 form은 한 번에 하나만 열립니다.
    const [createOpen, setCreateOpen] = useState(false)
    const [title, setTitle] = useState('')
    // 고른 담당 조원의 조 참여 번호입니다. select의 값은 글자라서 보낼 때 숫자로 바꿉니다.
    const [assigneeId, setAssigneeId] = useState('')
    const [creating, setCreating] = useState(false)
    const [memo, setMemo] = useState('')
    // 열어 둔 확인 상자입니다. 'report'는 완료 보고, 'cancel'은 항목 취소, null은 닫힌 상태입니다.
    const [confirming, setConfirming] = useState(null)
    const [reporting, setReporting] = useState(false)
    const [canceling, setCanceling] = useState(false)
    const [confirmations, setConfirmations] = useState([])
    // 완료확인과 확인 취소는 서로 끝날 때까지 막습니다.
    const [confirmationBusy, setConfirmationBusy] = useState(false)

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

    // 항목을 열면 그 항목의 완료확인 이력을 불러옵니다. 확인이 없으면 빈 배열이 옵니다.
    useEffect(() => {
        if (selectedId === null) return undefined
        // 다른 줄을 빠르게 누르면 늦게 온 옛 응답이 이력을 덮어쓸 수 있습니다. 줄이 바뀌면 이전 요청의 결과를 버립니다.
        let ignore = false
        getContributionConfirmations(selectedId)
            .then((data) => {
                if (!ignore) setConfirmations(data)
            })
            .catch((err) => {
                if (!ignore) setActionError(err.message)
            })
        return () => {
            ignore = true
        }
    }, [selectedId])

    // 항목을 오른쪽 상세에 엽니다. 다른 줄의 메모와 이력, 확인 상자는 지웁니다.
    function handleSelect(contributionId) {
        setActionError(null)
        setSelectedId(contributionId)
        setMemo('')
        setConfirmations([])
        setConfirming(null)
        setCreateOpen(false)
    }

    // 오른쪽에 등록 form을 엽니다.
    function handleOpenCreate() {
        setActionError(null)
        setSelectedId(null)
        setCreateOpen(true)
    }

    // 기여 항목을 등록합니다. 성공하면 새 항목을 목록 맨 위에 더하고 form을 닫습니다. 새 항목이 보이도록 탭을 전체로 돌립니다.
    function handleCreate() {
        setActionError(null)
        setCreating(true)
        createContribution(teamId, title, Number(assigneeId))
            .then((created) => {
                setContributions([created, ...contributions])
                setTitle('')
                setAssigneeId('')
                setCreateOpen(false)
                setStatusTab('전체')
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setCreating(false))
    }

    // 완료 보고합니다. 성공하면 그 줄만 서버가 돌려준 값으로 바꿉니다.
    function handleReport() {
        setActionError(null)
        setReporting(true)
        reportContribution(selectedId, memo)
            .then((updated) => {
                setContributions(contributions.map((contribution) => (contribution.id === updated.id ? updated : contribution)))
                setMemo('')
                setConfirming(null)
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setReporting(false))
    }

    // 항목을 취소합니다. 성공하면 그 줄만 바꿉니다. 목록에서 빼지 않습니다.
    function handleCancel() {
        setActionError(null)
        setCanceling(true)
        cancelContribution(selectedId)
            .then((updated) => {
                setContributions(contributions.map((contribution) => (contribution.id === updated.id ? updated : contribution)))
                setMemo('')
                setConfirming(null)
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setCanceling(false))
    }

    // 열어 둔 항목을 확인합니다. 성공하면 새 확인을 이력 맨 위(최근)에 더합니다.
    function handleConfirm() {
        setActionError(null)
        setConfirmationBusy(true)
        confirmContribution(selectedId)
            .then((created) => setConfirmations([created, ...confirmations]))
            .catch((err) => setActionError(err.message))
            .finally(() => setConfirmationBusy(false))
    }

    // 내 확인을 취소합니다. 성공하면 그 줄만 서버가 돌려준 값으로 바꿉니다. 이력에서 빼지 않습니다.
    function handleCancelConfirmation(target) {
        setActionError(null)
        setConfirmationBusy(true)
        cancelConfirmation(target.id)
            .then((updated) => {
                setConfirmations(confirmations.map((confirmation) => (confirmation.id === updated.id ? updated : confirmation)))
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setConfirmationBusy(false))
    }

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    // 내 조 참여 기록입니다. 교수는 조원이 아니라서 없습니다(undefined).
    const myTeamMember = members.find((member) => member.memberId === me.id)
    const isRecording = team.phase === 'RECORDING'
    const isRecordingLeader = isRecording && myTeamMember !== undefined && myTeamMember.leader
    // 등록, 보고, 취소는 서로 끝날 때까지 막습니다. 동시에 끝나면 나중에 끝난 쪽이 옛 contributions로 덮어써 먼저 끝난 결과가 사라지기 때문입니다.
    const busy = creating || reporting || canceling
    const shownContributions = statusTab === '전체' ? contributions : contributions.filter((contribution) => contributionStatusOf(contribution) === statusTab)
    const selectedContribution = contributions.find((contribution) => contribution.id === selectedId)

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, '기여 항목']}>
                {isRecordingLeader && <button type="button" className="btn primary" onClick={handleOpenCreate}><Icon name="plus" />항목 등록</button>}
            </TopBar>
            {!isRecording && <div className="lockband">평가 단계입니다. 기여 항목을 더 등록, 보고, 취소, 완료확인할 수 없습니다.</div>}
            {actionError && <div className="errband" role="alert">{actionError}</div>}
            <div className={selectedContribution || createOpen ? 'main-row detail-open' : 'main-row'}>
                <main className="content">
                    <div className="page-head">
                        <div>
                            <h1>기여 항목</h1>
                            <p>회의 진행, 자료 취합처럼 파일이 남지 않는 일의 기록입니다.</p>
                        </div>
                        <div className="tabs">
                            {STATUS_TABS.map((tab) => (
                                <button key={tab} type="button" className={statusTab === tab ? 'tab on' : 'tab'} onClick={() => setStatusTab(tab)}>{tab}</button>
                            ))}
                        </div>
                    </div>
                    <ContributionList
                        contributions={shownContributions}
                        emptyTitle={contributions.length === 0 ? '아직 등록한 기여 항목이 없습니다' : '이 상태의 항목이 없습니다'}
                        selectedId={selectedId}
                        onSelect={handleSelect}
                    />
                </main>
                {createOpen && (
                    <DetailPanel title="기여 항목 등록" onClose={() => setCreateOpen(false)}>
                        <ContributionCreateForm
                            title={title}
                            assigneeId={assigneeId}
                            members={members}
                            onTitleChange={setTitle}
                            onAssigneeChange={setAssigneeId}
                            onSubmit={handleCreate}
                            creating={busy}
                        />
                    </DetailPanel>
                )}
                {selectedContribution && (
                    <DetailPanel title={selectedContribution.title} onClose={() => setSelectedId(null)}>
                        {confirming === 'report' && (
                            <ConfirmBox
                                title="완료 보고할까요?"
                                body="보고한 뒤에는 메모를 고칠 수 없습니다."
                                okLabel="완료 보고"
                                okClass="primary"
                                onOk={handleReport}
                                onCancel={() => setConfirming(null)}
                                busy={busy}
                            />
                        )}
                        {confirming === 'cancel' && (
                            <ConfirmBox
                                title="이 항목을 취소할까요?"
                                body={'되돌릴 수 없고, 기록은 "취소됨"으로 남습니다.'}
                                okLabel="항목 취소"
                                okClass="danger"
                                onOk={handleCancel}
                                onCancel={() => setConfirming(null)}
                                busy={busy}
                            />
                        )}
                        <ContributionDetail
                            contribution={selectedContribution}
                            currentMemberId={me.id}
                            isMember={myTeamMember !== undefined}
                            isRecording={isRecording}
                            isRecordingLeader={isRecordingLeader}
                            memo={memo}
                            onMemoChange={setMemo}
                            onReport={() => setConfirming('report')}
                            onCancel={() => setConfirming('cancel')}
                            busy={busy}
                            confirmations={confirmations}
                            onConfirm={handleConfirm}
                            onCancelConfirmation={handleCancelConfirmation}
                            confirmationBusy={confirmationBusy}
                        />
                    </DetailPanel>
                )}
            </div>
        </>
    )
}

export default ContributionsPage
