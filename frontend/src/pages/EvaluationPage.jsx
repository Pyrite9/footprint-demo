import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { getMe } from '../api/auth'
import { getContributionConfirmations, getDeliverableConfirmations } from '../api/confirmations'
import { downloadDeliverable, getDeliverables } from '../api/deliverables'
import { getMyEvaluationDrafts, getMyEvaluationStatus, saveEvaluationDrafts, submitEvaluations } from '../api/evaluations'
import { getMemberEvidence } from '../api/evidence'
import { getTeam, getTeamMembers } from '../api/teams'
import ContributionDetail from '../components/ContributionDetail'
import DeliverableDetail from '../components/DeliverableDetail'
import DetailPanel from '../components/DetailPanel'
import EvaluationNotice from '../components/EvaluationNotice'
import EvaluationReview from '../components/EvaluationReview'
import EvaluationTargetForm from '../components/EvaluationTargetForm'
import EvaluationTargets from '../components/EvaluationTargets'
import EvidencePanel from '../components/EvidencePanel'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import TopBar from '../components/TopBar'
import { EVALUATION_CRITERIA } from '../constants/peerEvaluation'
import { formatDateTime } from '../format'
import { saveBlob } from '../saveBlob'

// 동료평가 화면입니다. 평가 단계에서 자신을 뺀 조원 전원을 한 명씩 입력 → 확인 화면에서 한 번에 보고 → 제출합니다. 제출한 평가는 고칠 수 없습니다.
// 입력 중에는 일부만 채워도 임시 저장할 수 있고, 다시 열면 저장한 내용부터 이어서 입력합니다.
// 입력하는 동안 오른쪽 근거 패널에서 고른 조원의 기록을 봅니다. 시스템은 기록을 보여 줄 뿐이고 점수는 평가자가 정합니다.
function EvaluationPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [status, setStatus] = useState(null)
    // 조원별 입력값입니다. 모양: { [조 참여 번호]: { goalSharing: 5, ..., comment: '' } }. 고르지 않은 점수는 null입니다.
    const [inputs, setInputs] = useState({})
    // 지금 입력 중인 조원의 조 참여 번호입니다. 처음에는 고르지 않아서(null) 첫 조원을 보여 줍니다.
    const [selectedTargetId, setSelectedTargetId] = useState(null)
    // true이면 입력 화면 대신 확인 화면을 보여 줍니다.
    const [reviewing, setReviewing] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    // 마지막으로 임시 저장한 시각입니다. 저장한 적이 없으면 null입니다.
    const [draftSavedAt, setDraftSavedAt] = useState(null)
    const [savingDraft, setSavingDraft] = useState(false)
    // 역할별 순번을 계산할 조 전체의 산출물입니다.
    const [teamDeliverables, setTeamDeliverables] = useState([])
    // 근거 패널을 열어 두었으면 true입니다. 입력 form과 나란히 둘 폭(1261px 이상)이면 처음부터 열고, 좁으면 form을 가리므로 닫아 둡니다.
    const [evidenceOpen, setEvidenceOpen] = useState(() => window.matchMedia('(min-width: 1261px)').matches)
    const [evidence, setEvidence] = useState(null)
    const [evidenceError, setEvidenceError] = useState(null)
    // 근거 패널에서 연 기록입니다. 모양: { type: 'deliverable' 또는 'contribution', record: 기록 }. 열지 않았으면 null입니다.
    const [selectedRecord, setSelectedRecord] = useState(null)
    const [recordConfirmations, setRecordConfirmations] = useState([])
    const [downloading, setDownloading] = useState(false)

    // 아래 값들은 근거를 불러오는 useEffect에도 쓰여서 "불러오는 중" return보다 위에서 계산합니다. 불러오기 전에는 team과 status가 null입니다.
    // 평가 대상(나를 뺀 조원)과 고른 조원은 계산할 수 있는 값이라 state로 두지 않습니다.
    const targets = members.filter((member) => member.memberId !== me?.id)
    const selectedTarget = targets.find((target) => target.id === selectedTargetId) ?? targets[0]
    // 입력은 평가 단계이고 아직 제출하지 않았을 때만 합니다.
    const canInput = team?.phase === 'EVALUATION' && status?.submitted === false
    const canOpenEvidence = canInput && !reviewing && selectedTarget !== undefined
    const showEvidence = canOpenEvidence && evidenceOpen
    const evidenceTargetId = showEvidence ? selectedTarget.id : null

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getMyEvaluationStatus(teamId), getMyEvaluationDrafts(teamId), getDeliverables(teamId)])
            .then(([meData, teamData, membersData, statusData, draftsData, deliverablesData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setStatus(statusData)
                setInputs(createInputs(membersData.filter((member) => member.memberId !== meData.id), draftsData))
                // 한 번에 저장한 내용은 시각이 모두 같아서 첫 줄의 시각을 씁니다.
                if (draftsData.length > 0) setDraftSavedAt(draftsData[0].savedAt)
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

    // 평가할 조원을 바꿉니다. 이전 조원의 근거가 잠깐이라도 보이지 않게 지웁니다.
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

    // 파일을 받아 브라우저의 "다운로드"로 저장합니다.
    function handleDownload(deliverable) {
        setActionError(null)
        setDownloading(true)
        downloadDeliverable(deliverable.id)
            .then((blob) => saveBlob(blob, deliverable.fileName))
            .catch((err) => setActionError(err.message))
            .finally(() => setDownloading(false))
    }

    // 조원 한 명의 한 항목 점수를 바꿉니다. 바깥 객체와 그 조원의 객체를 모두 새로 만들어야 React가 바뀐 것을 압니다.
    function handleScoreChange(targetId, key, score) {
        setInputs({ ...inputs, [targetId]: { ...inputs[targetId], [key]: score } })
    }

    // 조원 한 명의 서술 의견을 바꿉니다.
    function handleCommentChange(targetId, comment) {
        setInputs({ ...inputs, [targetId]: { ...inputs[targetId], comment } })
    }

    // 지금까지 입력한 내용을 임시 저장합니다. 고르지 않은 점수는 null 그대로 보냅니다. 입력값은 화면에 있는 그대로 둡니다.
    function handleSaveDraft(targets) {
        setActionError(null)
        setSavingDraft(true)
        const evaluations = targets.map((target) => ({ targetTeamMemberId: target.id, ...inputs[target.id] }))
        saveEvaluationDrafts(teamId, evaluations)
            .then((data) => setDraftSavedAt(data[0].savedAt))
            .catch((err) => setActionError(err.message))
            .finally(() => setSavingDraft(false))
    }

    // 확인 화면의 내용을 한 번에 제출합니다. 성공하면 제출 여부가 바뀌어 "제출했습니다" 화면이 됩니다.
    function handleSubmit(targets) {
        setActionError(null)
        setSubmitting(true)
        const evaluations = targets.map((target) => ({ targetTeamMemberId: target.id, ...inputs[target.id] }))
        submitEvaluations(teamId, evaluations)
            .then((data) => setStatus(data))
            .catch((err) => setActionError(err.message))
            .finally(() => setSubmitting(false))
    }

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    // 아직 다 고르지 않은 조원은 계산할 수 있는 값이라 state로 두지 않습니다.
    const notFilledTargets = targets.filter((target) => !isFilled(inputs[target.id]))
    const isEvaluationPhase = team.phase === 'EVALUATION'

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, '동료평가']} />
            {actionError && <div className="errband" role="alert">{actionError}</div>}
            <div className={showEvidence ? 'main-row detail-open' : 'main-row'}>
                <main className="content">
                    <div className="page-head">
                        <h1>동료평가</h1>
                        {status.submitted && <span className="pill green">제출함 · {formatDateTime(status.submittedAt)}</span>}
                        {canOpenEvidence && !evidenceOpen && <button type="button" className="btn" onClick={() => setEvidenceOpen(true)}>근거 기록 보기</button>}
                    </div>

                    {isEvaluationPhase && <div className="vis">평가받는 조원을 포함해 조원은 볼 수 없습니다. 교수는 평가자 이름, 점수, 의견을 봅니다.</div>}
                    <EvaluationNotice />

                    {status.submitted && (
                        <section className="card empty">
                            <b>동료평가를 제출했습니다</b>
                            <p className="sub">제출 시각: {formatDateTime(status.submittedAt)}. 제출한 평가는 고칠 수 없습니다.</p>
                        </section>
                    )}

                    {!isEvaluationPhase && (
                        <section className="card empty">
                            <b>아직 기록 단계입니다</b>
                            <p className="sub">동료평가는 평가 단계에서 입력합니다. 과제를 만든 교수가 평가를 시작하면 입력할 수 있습니다.</p>
                        </section>
                    )}

                    {canInput && targets.length === 0 && (
                        <section className="card empty">
                            <b>평가할 조원이 없습니다</b>
                            <p className="sub">조에 자신만 있어서 제출할 평가가 없습니다.</p>
                        </section>
                    )}

                    {canInput && targets.length > 0 && !reviewing && (
                        <>
                            <EvaluationTargets
                                targets={targets}
                                inputs={inputs}
                                selectedId={selectedTarget.id}
                                onSelect={handleSelectTarget}
                            />
                            <section className="crit-list">
                                <EvaluationTargetForm
                                    key={selectedTarget.id}
                                    input={inputs[selectedTarget.id]}
                                    onScoreChange={(key, score) => handleScoreChange(selectedTarget.id, key, score)}
                                    onCommentChange={(comment) => handleCommentChange(selectedTarget.id, comment)}
                                />
                                <div className="crit">
                                    <div className="row between">
                                        <div>
                                            <p className="sub">
                                                {notFilledTargets.length > 0
                                                    ? `조원 전원의 6개 항목을 모두 입력해야 다음으로 넘어갑니다. 남은 조원: ${notFilledTargets.map((target) => target.name).join(', ')}`
                                                    : '입력 내용을 확인한 뒤 전원을 한 번에 제출합니다.'}
                                            </p>
                                            <p className="sub small">임시 저장한 내용은 교수에게 보이지 않고, 제출하기 전까지 미제출로 남습니다.</p>
                                            {draftSavedAt && <p className="sub small">임시 저장 시각: {formatDateTime(draftSavedAt)}</p>}
                                        </div>
                                        <div className="row">
                                            <button type="button" className="btn" onClick={() => handleSaveDraft(targets)} disabled={savingDraft}>임시 저장</button>
                                            <button type="button" className="btn primary" onClick={() => setReviewing(true)} disabled={savingDraft || notFilledTargets.length > 0}>입력 내용 확인</button>
                                        </div>
                                    </div>
                                </div>
                            </section>
                        </>
                    )}

                    {canInput && reviewing && (
                        <section className="crit-list">
                            <EvaluationReview targets={targets} inputs={inputs} />
                            <div className="confirm">
                                <h2>이 내용으로 전원의 평가를 제출할까요?</h2>
                                <p className="sub">제출한 평가는 고칠 수 없습니다.</p>
                                <div className="row">
                                    <button type="button" className="btn primary" onClick={() => handleSubmit(targets)} disabled={submitting}>전원 제출</button>
                                    <button type="button" className="btn" onClick={() => setReviewing(false)} disabled={submitting}>돌아가서 고치기</button>
                                </div>
                            </div>
                        </section>
                    )}
                </main>
                {showEvidence && selectedRecord === null && (
                    <DetailPanel title={`${selectedTarget.name}의 근거 기록`} onClose={handleCloseEvidence}>
                        {evidenceError && <p className="sub">{evidenceError}</p>}
                        {!evidenceError && evidence === null && <p className="sub">불러오는 중...</p>}
                        {evidence !== null && (
                            <EvidencePanel
                                evidence={evidence}
                                teamDeliverables={teamDeliverables}
                                onSelectDeliverable={(deliverable) => handleSelectRecord('deliverable', deliverable)}
                                onSelectContribution={(contribution) => handleSelectRecord('contribution', contribution)}
                            />
                        )}
                    </DetailPanel>
                )}
                {showEvidence && selectedRecord !== null && selectedRecord.type === 'deliverable' && (
                    <DetailPanel title={selectedRecord.record.fileName} onClose={handleCloseEvidence}>
                        <div><button type="button" className="btn" onClick={() => setSelectedRecord(null)}>← 근거 기록으로</button></div>
                        <DeliverableDetail
                            deliverable={selectedRecord.record}
                            deliverables={teamDeliverables}
                            currentMemberId={me.id}
                            isMember
                            isRecording={false}
                            onDownload={handleDownload}
                            downloading={downloading}
                            confirmations={recordConfirmations}
                        />
                    </DetailPanel>
                )}
                {showEvidence && selectedRecord !== null && selectedRecord.type === 'contribution' && (
                    <DetailPanel title={selectedRecord.record.title} onClose={handleCloseEvidence}>
                        <div><button type="button" className="btn" onClick={() => setSelectedRecord(null)}>← 근거 기록으로</button></div>
                        <ContributionDetail
                            contribution={selectedRecord.record}
                            currentMemberId={me.id}
                            isMember
                            isRecording={false}
                            isRecordingLeader={false}
                            confirmations={recordConfirmations}
                        />
                    </DetailPanel>
                )}
            </div>
        </>
    )
}

// 평가 대상마다 입력값을 만듭니다. 임시 저장이 있으면 그 값으로 채우고, 없으면 점수는 null(고르지 않음), 의견은 빈 글자입니다.
function createInputs(targets, drafts) {
    const inputs = {}
    for (const target of targets) {
        const draft = drafts.find((item) => item.targetTeamMemberId === target.id)
        // 서버는 빈 의견을 null로 돌려줍니다. textarea의 value는 글자여야 해서 빈 글자로 바꿉니다.
        const input = { comment: draft?.comment ?? '' }
        EVALUATION_CRITERIA.forEach((criterion) => { input[criterion.key] = draft ? draft[criterion.key] : null })
        inputs[target.id] = input
    }
    return inputs
}

// 한 조원의 6개 항목 점수를 모두 골랐으면 true입니다.
function isFilled(input) {
    return EVALUATION_CRITERIA.every((criterion) => input[criterion.key] !== null)
}

export default EvaluationPage
