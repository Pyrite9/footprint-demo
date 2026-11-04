import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getMe } from '../api/auth'
import { getMyEvaluationDrafts, getMyEvaluationStatus, saveEvaluationDrafts, submitEvaluations } from '../api/evaluations'
import { getTeam, getTeamMembers } from '../api/teams'
import EvaluationNotice from '../components/EvaluationNotice'
import EvaluationReview from '../components/EvaluationReview'
import EvaluationTargetForm from '../components/EvaluationTargetForm'
import { EVALUATION_CRITERIA } from '../constants/peerEvaluation'

// 동료평가 화면입니다. 평가 단계에서 자신을 뺀 조원 전원을 입력 → 확인 화면에서 한 번에 보고 → 제출합니다. 제출한 평가는 고칠 수 없습니다.
// 입력 중에는 일부만 채워도 임시 저장할 수 있고, 다시 열면 저장한 내용부터 이어서 입력합니다.
function EvaluationPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [status, setStatus] = useState(null)
    // 조원별 입력값입니다. 모양: { [조 참여 번호]: { goalSharing: 5, ..., comment: '' } }. 고르지 않은 점수는 null입니다.
    const [inputs, setInputs] = useState({})
    // true이면 입력 화면 대신 확인 화면을 보여 줍니다.
    const [reviewing, setReviewing] = useState(false)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [actionError, setActionError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    // 마지막으로 임시 저장한 시각입니다. 저장한 적이 없으면 null입니다.
    const [draftSavedAt, setDraftSavedAt] = useState(null)
    const [savingDraft, setSavingDraft] = useState(false)

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getMyEvaluationStatus(teamId), getMyEvaluationDrafts(teamId)])
            .then(([meData, teamData, membersData, statusData, draftsData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setStatus(statusData)
                setInputs(createInputs(membersData.filter((member) => member.memberId !== meData.id), draftsData))
                // 한 번에 저장한 내용은 시각이 모두 같아서 첫 줄의 시각을 씁니다.
                if (draftsData.length > 0) setDraftSavedAt(draftsData[0].savedAt)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

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

    // 모든 조원의 6개 항목을 골랐으면 확인 화면으로 넘어갑니다. 빠진 조원이 있으면 이름을 알려 줍니다.
    function handleReview(notFilledTargets) {
        if (notFilledTargets.length > 0) {
            setActionError('6개 항목을 모두 고르지 않은 조원이 있습니다: ' + notFilledTargets.map((target) => target.name).join(', '))
            return
        }
        setActionError(null)
        setReviewing(true)
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

    if (loading) return <main><p>불러오는 중...</p></main>
    if (error) {
        return (
            <main>
                <p className="error">{error}</p>
                <p><Link to="/">과제 목록으로</Link></p>
            </main>
        )
    }

    // 평가 대상(나를 뺀 조원)과 아직 다 고르지 않은 조원은 계산할 수 있는 값이라 state로 두지 않습니다.
    const targets = members.filter((member) => member.memberId !== me.id)
    const notFilledTargets = targets.filter((target) => !isFilled(inputs[target.id]))
    // 입력은 평가 단계이고 아직 제출하지 않았을 때만 합니다.
    const canInput = team.phase === 'EVALUATION' && !status.submitted

    return (
        <main>
            <p className="links"><Link to={`/teams/${team.id}`}>← {team.name}</Link></p>
            <h1>{team.name} 동료평가</h1>
            <EvaluationNotice />

            {status.submitted && (
                <p className="success">동료평가를 제출했습니다. 제출 시각: {status.submittedAt.replace('T', ' ')}. 제출한 평가는 고칠 수 없습니다.</p>
            )}

            {team.phase === 'RECORDING' && <p className="notice">동료평가는 평가 단계에서 입력합니다. 과제를 만든 교수가 평가를 시작하면 입력할 수 있습니다.</p>}

            {canInput && targets.length === 0 && <p>평가할 조원이 없습니다.</p>}

            {canInput && targets.length > 0 && !reviewing && (
                <>
                    <p>자신을 뺀 조원 {targets.length}명을 모두 평가해야 제출할 수 있습니다. 평가는 과제를 만든 교수만 봅니다.</p>
                    <p className="muted">임시 저장한 내용은 교수에게 보이지 않고, 제출하기 전까지 미제출로 남습니다.</p>
                    {targets.map((target) => (
                        <EvaluationTargetForm
                            key={target.id}
                            target={target}
                            input={inputs[target.id]}
                            onScoreChange={(key, score) => handleScoreChange(target.id, key, score)}
                            onCommentChange={(comment) => handleCommentChange(target.id, comment)}
                        />
                    ))}
                    <div className="row">
                        <button type="button" className="secondary" onClick={() => handleSaveDraft(targets)} disabled={savingDraft}>임시 저장</button>
                        <button type="button" onClick={() => handleReview(notFilledTargets)} disabled={savingDraft}>입력 내용 확인</button>
                    </div>
                    {draftSavedAt && <p className="muted">임시 저장 시각: {draftSavedAt.replace('T', ' ')}</p>}
                </>
            )}

            {canInput && reviewing && (
                <>
                    <p>아래 내용으로 제출합니다. 제출한 평가는 고칠 수 없습니다.</p>
                    <EvaluationReview targets={targets} inputs={inputs} />
                    <div className="row">
                        <button type="button" className="secondary" onClick={() => setReviewing(false)} disabled={submitting}>고치기</button>
                        <button type="button" onClick={() => handleSubmit(targets)} disabled={submitting}>제출</button>
                    </div>
                </>
            )}

            {actionError && <p className="error">{actionError}</p>}
        </main>
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
