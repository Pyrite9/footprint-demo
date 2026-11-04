import { EVALUATION_CRITERIA } from '../constants/peerEvaluation'
import { initialOf } from '../format'

// 평가할 조원을 고르는 칩을 그립니다. 칩마다 입력 상태를 보여 줍니다. 데이터와 동작은 page에서 props로 받습니다.
function EvaluationTargets({ targets, inputs, selectedId, onSelect }) {
    return (
        <div className="targets">
            {targets.map((target) => {
                const status = inputStatusOf(inputs[target.id])

                return (
                    <button key={target.id} type="button" className={target.id === selectedId ? 'target on' : 'target'} onClick={() => onSelect(target.id)}>
                        <span className="av">{initialOf(target.name)}</span>
                        <span>{target.name}</span>
                        <span className={`pill ${status.pill}`}>{status.label}</span>
                    </button>
                )
            })}
        </div>
    )
}

// 한 조원의 입력 상태를 고른 항목 수로 정합니다. 점수를 더하는 것이 아니라 빈칸이 남았는지만 봅니다.
function inputStatusOf(input) {
    const chosen = EVALUATION_CRITERIA.filter((criterion) => input[criterion.key] !== null).length
    if (chosen === EVALUATION_CRITERIA.length) return { label: '입력함', pill: 'green' }
    if (chosen > 0) return { label: '입력 중', pill: 'amber' }
    return { label: '미입력', pill: 'gray' }
}

export default EvaluationTargets
