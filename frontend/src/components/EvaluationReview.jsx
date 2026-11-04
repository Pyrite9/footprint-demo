import { EVALUATION_CRITERIA } from '../constants/peerEvaluation'
import { initialOf } from '../format'

// 제출 전에 대상 조원 전원의 입력 내용을 한 사람씩 보여 줍니다. 점수를 더하거나 평균 내지 않습니다.
function EvaluationReview({ targets, inputs }) {
    return targets.map((target) => (
        <div key={target.id} className="crit">
            <div className="row">
                <span className="av">{initialOf(target.name)}</span>
                <h3>{target.name}</h3>
            </div>
            <div className="review-cells">
                {EVALUATION_CRITERIA.map((criterion) => (
                    <div key={criterion.key} className="review-cell">
                        <span>{criterion.name}</span>
                        <b>{inputs[target.id][criterion.key]}점</b>
                    </div>
                ))}
            </div>
            {inputs[target.id].comment.trim() !== '' && <div className="memo">{inputs[target.id].comment}</div>}
        </div>
    ))
}

export default EvaluationReview
