import { describeScore, EVALUATION_CRITERIA, SCORE_OPTIONS } from '../constants/peerEvaluation'

// 조원 한 명에 대한 6개 항목 점수와 서술 의견을 입력합니다. 입력값과 바꾸는 동작은 page에서 props로 받습니다.
// input: { goalSharing, roleFulfillment, ..., comment }. 아직 고르지 않은 점수는 null입니다.
function EvaluationTargetForm({ target, input, onScoreChange, onCommentChange }) {
    return (
        <section>
            <h2>{target.name} ({target.roleName ?? '역할 없음'})</h2>
            {EVALUATION_CRITERIA.map((criterion) => (
                <fieldset key={criterion.key}>
                    <legend>{criterion.name}</legend>
                    {SCORE_OPTIONS.map((score) => (
                        <label key={score} style={{ display: 'block' }}>
                            {/* name이 같은 radio는 하나만 고를 수 있습니다. 조원마다, 항목마다 다른 name을 줍니다. */}
                            <input
                                type="radio"
                                name={`${target.id}-${criterion.key}`}
                                checked={input[criterion.key] === score}
                                onChange={() => onScoreChange(criterion.key, score)}
                            />
                            {' '}{score}점: {describeScore(criterion, score)}
                        </label>
                    ))}
                </fieldset>
            ))}
            <label style={{ display: 'block' }}>
                서술 의견(선택, 2000자까지)
                <br />
                <textarea
                    value={input.comment}
                    maxLength={2000}
                    rows={3}
                    cols={60}
                    onChange={(event) => onCommentChange(event.target.value)}
                />
            </label>
        </section>
    )
}

export default EvaluationTargetForm
