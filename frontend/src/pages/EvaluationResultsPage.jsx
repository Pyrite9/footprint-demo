import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { getMe } from '../api/auth'
import { getContributionConfirmations, getDeliverableConfirmations } from '../api/confirmations'
import { downloadDeliverable, getDeliverables } from '../api/deliverables'
import { downloadEvaluationCsv, getEvaluationSubmissions, getTeamEvaluations } from '../api/evaluations'
import { getMemberEvidence } from '../api/evidence'
import { getTeam, getTeamMembers } from '../api/teams'
import EvaluationNotice from '../components/EvaluationNotice'
import EvaluationResultList from '../components/EvaluationResultList'
import EvaluationSubmissionList from '../components/EvaluationSubmissionList'
import EvidenceSidePanel from '../components/EvidenceSidePanel'
import Icon from '../components/Icon'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import TopBar from '../components/TopBar'
import { initialOf } from '../format'
import { saveBlob } from '../saveBlob'

// 과제를 만든 교수의 평가 결과 화면입니다. 조원별 제출 여부와 평가자별 점수·의견을 입력된 그대로 보여 주고, 옆에 대상 조원의 근거 패널을 띄웁니다(서버가 교수인지 확인합니다).
// 시스템은 합계, 평균, 순위를 계산하지 않습니다. 판단은 교수가 합니다.
function EvaluationResultsPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [submissions, setSubmissions] = useState([])
    const [evaluations, setEvaluations] = useState([])
    // 역할별 순번을 계산할 조 전체의 산출물입니다.
    const [teamDeliverables, setTeamDeliverables] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    const [selectedTargetId, setSelectedTargetId] = useState(null)
    // 근거 패널을 열어 두었으면 true입니다. 결과와 나란히 둘 폭(1261px 이상)이면 처음부터 열고, 좁으면 결과를 가리므로 닫아 둡니다.
    const [evidenceOpen, setEvidenceOpen] = useState(() => window.matchMedia('(min-width: 1261px)').matches)
    const [evidence, setEvidence] = useState(null)
    const [evidenceError, setEvidenceError] = useState(null)
    // 근거 패널에서 연 기록입니다. 모양: { type: 'deliverable' 또는 'contribution', record: 기록 }. 열지 않았으면 null입니다.
    const [selectedRecord, setSelectedRecord] = useState(null)
    const [recordConfirmations, setRecordConfirmations] = useState([])
    const [downloading, setDownloading] = useState(false)
    const [exporting, setExporting] = useState(false)

    // 아래 값들은 근거를 불러오는 useEffect에도 쓰여서 "불러오는 중" return보다 위에서 계산합니다. 불러오기 전에는 team이 null입니다.
    const selectedTarget = members.find((member) => member.id === selectedTargetId) ?? members[0]
    const isEvaluationPhase = team?.phase === 'EVALUATION'
    const showEvidence = isEvaluationPhase && evidenceOpen && selectedTarget !== undefined
    const evidenceTargetId = showEvidence ? selectedTarget.id : null

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getEvaluationSubmissions(teamId), getTeamEvaluations(teamId), getDeliverables(teamId)])
            .then(([meData, teamData, membersData, submissionsData, evaluationsData, deliverablesData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setSubmissions(submissionsData)
                setEvaluations(evaluationsData)
                setTeamDeliverables(deliverablesData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    // 근거 패널이 열려 있으면 고른 조원의 기록을 불러옵니다.
    useEffect(() => {
        if (evidenceTargetId === null) return undefined
        // 조원을 빠르게 바꾸면 늦게 온 옛 응답이 다른 조원의 기록을 덮어쓸 수 있습니다. 조원이 바뀌면 이전 요청의 결과를 버립니다.
        let ignore = false
        getMemberEvidence(teamId, evidenceTargetId)
            .then((data) => {
                if (ignore) return
                setEvidence(data)
                // 패널을 닫았다가 다시 열어 성공하면 전에 난 오류를 지웁니다.
                setEvidenceError(null)
            })
            .catch((err) => {
                if (!ignore) setEvidenceError(err.message)
            })
        return () => {
            ignore = true
        }
    }, [teamId, evidenceTargetId])

    // 근거 패널에서 기록을 열면 그 기록의 완료확인 이력을 불러옵니다.
    useEffect(() => {
        if (selectedRecord === null) return undefined
        let ignore = false
        const loadConfirmations = selectedRecord.type === 'deliverable' ? getDeliverableConfirmations : getContributionConfirmations
        loadConfirmations(selectedRecord.record.id)
            .then((data) => {
                if (!ignore) setRecordConfirmations(data)
            })
            .catch((err) => {
                if (!ignore) setActionError(err.message)
            })
        return () => {
            ignore = true
        }
    }, [selectedRecord])

    // 결과를 볼 조원을 바꿉니다. 이전 조원의 근거가 잠깐이라도 보이지 않게 지웁니다.
    function handleSelectTarget(targetId) {
        setSelectedTargetId(targetId)
        setEvidence(null)
        setEvidenceError(null)
        setSelectedRecord(null)
    }

    // 근거 패널을 닫습니다. 다시 열면 기록을 새로 불러옵니다.
    function handleCloseEvidence() {
        setEvidenceOpen(false)
        setSelectedRecord(null)
    }

    // 근거 패널에서 기록 하나를 엽니다. 다른 기록의 이력은 지웁니다.
    function handleSelectRecord(type, record) {
        setActionError(null)
        setRecordConfirmations([])
        setSelectedRecord({ type, record })
    }

    // 산출물 파일을 받아 브라우저의 "다운로드"로 저장합니다.
    function handleDownload(deliverable) {
        setActionError(null)
        setDownloading(true)
        downloadDeliverable(deliverable.id)
            .then((blob) => saveBlob(blob, deliverable.fileName))
            .catch((err) => setActionError(err.message))
            .finally(() => setDownloading(false))
    }

    // 평가 결과 CSV를 받아 브라우저의 "다운로드"로 저장합니다.
    function handleExport() {
        setActionError(null)
        setExporting(true)
        downloadEvaluationCsv(teamId)
            .then((blob) => saveBlob(blob, `${team.assignmentTitle}-${team.name}-동료평가.csv`))
            .catch((err) => setActionError(err.message))
            .finally(() => setExporting(false))
    }

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, '평가 결과']}>
                {isEvaluationPhase && (
                    <button type="button" className="btn" onClick={handleExport} disabled={exporting}><Icon name="download" />CSV 내려받기</button>
                )}
            </TopBar>
            {actionError && <div className="errband" role="alert">{actionError}</div>}
            <div className={showEvidence ? 'main-row detail-open' : 'main-row'}>
                <main className="content">
                    <div className="page-head">
                        <div>
                            <h1>평가 결과</h1>
                            <p>평가자가 입력한 그대로 보여 줍니다. 합계, 평균, 순위는 계산하지 않습니다.</p>
                        </div>
                        {isEvaluationPhase && !evidenceOpen && <button type="button" className="btn" onClick={() => setEvidenceOpen(true)}>근거 기록 보기</button>}
                    </div>
                    <EvaluationNotice />

                    {!isEvaluationPhase && (
                        <section className="card empty">
                            <b>아직 기록 단계입니다</b>
                            <p className="sub">조 현황에서 평가를 시작하면 조원이 동료평가를 입력하고, 제출한 내용이 여기에 나옵니다.</p>
                        </section>
                    )}

                    {isEvaluationPhase && (
                        <>
                            <EvaluationSubmissionList submissions={submissions} />
                            <div className="targets">
                                {members.map((member) => (
                                    <button key={member.id} type="button" className={member.id === selectedTarget.id ? 'target on' : 'target'} onClick={() => handleSelectTarget(member.id)}>
                                        <span className="av">{initialOf(member.name)}</span>
                                        <span>{member.name}</span>
                                    </button>
                                ))}
                            </div>
                            <EvaluationResultList evaluations={evaluations.filter((evaluation) => evaluation.targetTeamMemberId === selectedTarget.id)} />
                        </>
                    )}
                </main>
                {showEvidence && (
                    <EvidenceSidePanel
                        targetName={selectedTarget.name}
                        evidence={evidence}
                        evidenceError={evidenceError}
                        teamDeliverables={teamDeliverables}
                        selectedRecord={selectedRecord}
                        recordConfirmations={recordConfirmations}
                        currentMemberId={me.id}
                        isMember={false}
                        onClose={handleCloseEvidence}
                        onSelectRecord={handleSelectRecord}
                        onBack={() => setSelectedRecord(null)}
                        onDownload={handleDownload}
                        downloading={downloading}
                    />
                )}
            </div>
        </>
    )
}

export default EvaluationResultsPage
