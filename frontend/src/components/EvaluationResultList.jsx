import { EVALUATION_CRITERIA } from '../constants/peerEvaluation'
import { formatShortDateTime, initialOf } from '../format'

// 한 조원이 받은 평가를 평가자별로 입력한 그대로 그립니다. 점수를 더하거나 평균 내지 않습니다. 데이터는 page에서 props로 받습니다.
function EvaluationResultList({ evaluations }) {
    if (evaluations.length === 0) {
        return <section className="card empty"><p className="sub">이 조원에 대해 제출된 평가가 아직 없습니다.</p></section>
    }

    return (
        <section className="crit-list">
            {evaluations.map((evaluation) => (
                <div key={evaluation.id} className="crit">
                    <div className="row between">
                        <div className="row">
                            <span className="av">{initialOf(evaluation.evaluatorName)}</span>
                            <h3>{evaluation.evaluatorName}</h3>
                        </div>
                        <span className="sub small">{formatShortDateTime(evaluation.submittedAt)}</span>
                    </div>
                    <div className="review-cells">
                        {EVALUATION_CRITERIA.map((criterion) => (
                            <div key={criterion.key} className="review-cell">
                                <span>{criterion.name}</span>
                                <b>{evaluation[criterion.key]}점</b>
                            </div>
                        ))}
                    </div>
                    {evaluation.comment !== null && <div className="memo">{evaluation.comment}</div>}
                </div>
            ))}
        </section>
    )
}

export default EvaluationResultList
