import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { getMe } from '../api/auth'
import { cancelReportPart, createReport, createReportPart, getReportParts, getReports } from '../api/reports'
import { getTeam, getTeamMembers } from '../api/teams'
import ConfirmBox from '../components/ConfirmBox'
import DetailPanel from '../components/DetailPanel'
import Icon from '../components/Icon'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import ReportCreateForm from '../components/ReportCreateForm'
import ReportList from '../components/ReportList'
import ReportPartCreateForm from '../components/ReportPartCreateForm'
import ReportPartDetail from '../components/ReportPartDetail'
import ReportPartList from '../components/ReportPartList'
import TopBar from '../components/TopBar'

// 조의 보고서 화면입니다. 보고서를 고르면 아래에 파트가 열립니다. 목록은 조원과 교수가 보고,
// 기록 단계에서 보고서 만들기와 파트 등록·취소는 조장이 합니다(서버가 확인합니다). 파트의 글쓰기는 X-12-2에서 더합니다.
function ReportsPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [reports, setReports] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    // 고른 보고서의 번호와 그 보고서의 파트입니다. 보고서를 바꾸면 파트를 다시 불러옵니다.
    const [selectedReportId, setSelectedReportId] = useState(null)
    const [parts, setParts] = useState([])
    // 오른쪽 상세에 연 파트의 번호입니다.
    const [selectedPartId, setSelectedPartId] = useState(null)
    // 오른쪽에 연 form입니다. 'report'는 보고서 만들기, 'part'는 파트 등록, null은 닫힌 상태입니다. 파트 상세와 form은 한 번에 하나만 열립니다.
    const [openForm, setOpenForm] = useState(null)
    const [reportTitle, setReportTitle] = useState('')
    const [partTitle, setPartTitle] = useState('')
    // 고른 담당 조원의 조 참여 번호입니다. select의 값은 글자라서 보낼 때 숫자로 바꿉니다.
    const [assigneeId, setAssigneeId] = useState('')
    const [creating, setCreating] = useState(false)
    const [canceling, setCanceling] = useState(false)
    // 파트 취소 확인 상자를 열어 두었으면 true입니다.
    const [confirmingCancel, setConfirmingCancel] = useState(false)

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getReports(teamId)])
            .then(([meData, teamData, membersData, reportsData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setReports(reportsData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    // 보고서를 고르면 그 보고서의 파트를 불러옵니다.
    useEffect(() => {
        if (selectedReportId === null) return undefined
        // 다른 보고서를 빠르게 누르면 늦게 온 옛 응답이 파트를 덮어쓸 수 있습니다. 보고서가 바뀌면 이전 요청의 결과를 버립니다.
        let ignore = false
        getReportParts(selectedReportId)
            .then((data) => {
                if (!ignore) setParts(data)
            })
            .catch((err) => {
                if (!ignore) setActionError(err.message)
            })
        return () => {
            ignore = true
        }
    }, [selectedReportId])

    // 보고서를 고릅니다. 이전 보고서의 파트와 오른쪽 상세는 닫습니다.
    function handleSelectReport(reportId) {
        setActionError(null)
        setSelectedReportId(reportId)
        setParts([])
        setSelectedPartId(null)
        setConfirmingCancel(false)
        if (openForm === 'part') setOpenForm(null)
    }

    // 파트를 오른쪽 상세에 엽니다.
    function handleSelectPart(partId) {
        setActionError(null)
        setSelectedPartId(partId)
        setConfirmingCancel(false)
        setOpenForm(null)
    }

    // 오른쪽에 form을 엽니다. kind는 'report' 또는 'part'입니다.
    function handleOpenForm(kind) {
        setActionError(null)
        setSelectedPartId(null)
        setConfirmingCancel(false)
        setOpenForm(kind)
    }

    // 보고서를 만듭니다. 성공하면 목록 맨 위에 더하고, 새 보고서를 골라 파트를 나눌 수 있게 합니다.
    function handleCreateReport() {
        setActionError(null)
        setCreating(true)
        createReport(teamId, reportTitle)
            .then((created) => {
                setReports([created, ...reports])
                setReportTitle('')
                setOpenForm(null)
                handleSelectReport(created.id)
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setCreating(false))
    }

    // 파트를 등록합니다. 성공하면 파트 목록 맨 뒤에 더합니다(등록 순서가 합본 순서입니다). form은 열어 둬서 다음 파트를 이어 등록합니다.
    function handleCreatePart() {
        setActionError(null)
        setCreating(true)
        createReportPart(selectedReportId, partTitle, Number(assigneeId))
            .then((created) => {
                setParts([...parts, created])
                setPartTitle('')
                setAssigneeId('')
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setCreating(false))
    }

    // 파트를 취소합니다. 성공하면 그 줄만 서버가 돌려준 값으로 바꿉니다. 목록에서 빼지 않습니다.
    function handleCancelPart() {
        setActionError(null)
        setCanceling(true)
        cancelReportPart(selectedPartId)
            .then((updated) => {
                setParts(parts.map((part) => (part.id === updated.id ? updated : part)))
                setConfirmingCancel(false)
            })
            .catch((err) => setActionError(err.message))
            .finally(() => setCanceling(false))
    }

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    // 내 조 참여 기록입니다. 교수는 조원이 아니라서 없습니다(undefined).
    const myTeamMember = members.find((member) => member.memberId === me.id)
    const isRecording = team.phase === 'RECORDING'
    const isRecordingLeader = isRecording && myTeamMember !== undefined && myTeamMember.leader
    // 만들기, 등록, 취소는 서로 끝날 때까지 막습니다. 동시에 끝나면 나중에 끝난 쪽이 옛 배열로 덮어써 먼저 끝난 결과가 사라지기 때문입니다.
    const busy = creating || canceling
    const selectedReport = reports.find((report) => report.id === selectedReportId)
    const selectedPart = parts.find((part) => part.id === selectedPartId)
    const detailOpen = openForm !== null || selectedPart !== undefined

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, '보고서']}>
                {isRecordingLeader && <button type="button" className="btn primary" onClick={() => handleOpenForm('report')}><Icon name="plus" />보고서 만들기</button>}
            </TopBar>
            {!isRecording && <div className="lockband">평가 단계입니다. 보고서와 파트를 더 만들거나 취소할 수 없습니다.</div>}
            {actionError && <div className="errband" role="alert">{actionError}</div>}
            <div className={detailOpen ? 'main-row detail-open' : 'main-row'}>
                <main className="content">
                    <div className="page-head">
                        <div>
                            <h1>보고서</h1>
                            <p>조장이 보고서를 파트로 나누고 파트마다 담당 조원 한 명을 정합니다.</p>
                        </div>
                    </div>
                    <ReportList reports={reports} selectedId={selectedReportId} onSelect={handleSelectReport} />
                    {selectedReport && (
                        <>
                            <div className="page-head">
                                <div>
                                    <h2>{selectedReport.title}의 파트</h2>
                                    <p>먼저 등록한 파트부터 나옵니다. 합칠 때도 이 순서입니다.</p>
                                </div>
                                {isRecordingLeader && <button type="button" className="btn" onClick={() => handleOpenForm('part')}><Icon name="plus" />파트 등록</button>}
                            </div>
                            <ReportPartList parts={parts} selectedId={selectedPartId} onSelect={handleSelectPart} />
                        </>
                    )}
                </main>
                {openForm === 'report' && (
                    <DetailPanel title="보고서 만들기" onClose={() => setOpenForm(null)}>
                        <ReportCreateForm title={reportTitle} onTitleChange={setReportTitle} onSubmit={handleCreateReport} creating={busy} />
                    </DetailPanel>
                )}
                {openForm === 'part' && selectedReport && (
                    <DetailPanel title={`${selectedReport.title} 파트 등록`} onClose={() => setOpenForm(null)}>
                        {/* key: 파트가 하나 늘면 form을 새로 그립니다. 그래야 비워진 칸이 "사용자가 건드린 틀린 칸"(:user-invalid)으로 빨갛게 남지 않습니다. */}
                        <ReportPartCreateForm
                            key={parts.length}
                            title={partTitle}
                            assigneeId={assigneeId}
                            members={members}
                            onTitleChange={setPartTitle}
                            onAssigneeChange={setAssigneeId}
                            onSubmit={handleCreatePart}
                            creating={busy}
                        />
                    </DetailPanel>
                )}
                {selectedPart && (
                    <DetailPanel title={selectedPart.title} onClose={() => setSelectedPartId(null)}>
                        {confirmingCancel && (
                            <ConfirmBox
                                title="이 파트를 취소할까요?"
                                body={'되돌릴 수 없고, 기록은 "취소됨"으로 남습니다. 합칠 때는 빠집니다.'}
                                okLabel="파트 취소"
                                okClass="danger"
                                onOk={handleCancelPart}
                                onCancel={() => setConfirmingCancel(false)}
                                busy={busy}
                            />
                        )}
                        <ReportPartDetail
                            part={selectedPart}
                            isRecordingLeader={isRecordingLeader}
                            onCancel={() => setConfirmingCancel(true)}
                            busy={busy}
                        />
                    </DetailPanel>
                )}
            </div>
        </>
    )
}

export default ReportsPage
