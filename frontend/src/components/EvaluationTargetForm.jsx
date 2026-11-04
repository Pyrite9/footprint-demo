import { describeScore, EVALUATION_CRITERIA, SCORE_OPTIONS } from '../constants/peerEvaluation'

// 조원 한 명에 대한 6개 항목 점수와 서술 의견을 입력합니다. 입력값과 바꾸는 동작은 page에서 props로 받습니다.
// input: { goalSharing, roleFulfillment, ..., comment }. 아직 고르지 않은 점수는 null입니다.
function EvaluationTargetForm({ input, onScoreChange, onCommentChange }) {
    return (
        <>
            {EVALUATION_CRITERIA.map((criterion) => {
                const chosen = input[criterion.key]

                return (
                    <div key={criterion.key} className="crit">
                        <h3>{criterion.name}</h3>
                        <div className="scores" role="group" aria-label={`${criterion.name} 점수`}>
                            {SCORE_OPTIONS.map((score) => (
                                // aria-pressed: 화면 읽기 프로그램에 지금 고른 점수를 알려 줍니다.
                                <button
                                    key={score}
                                    type="button"
                                    className={chosen === score ? 'score on' : 'score'}
                                    aria-pressed={chosen === score}
                                    onClick={() => onScoreChange(criterion.key, score)}
                                >
                                    {score}
                                </button>
                            ))}
                            {/* 2점과 4점은 기준 문장이 없어서 "중간"이라고 글로 알려 줍니다. */}
                            {(chosen === 2 || chosen === 4) && <span className="sub">{describeScore(criterion, chosen)}</span>}
                        </div>
                        <div className="anchors">
                            <span className={chosen === 5 ? 'hit' : undefined}><b>5점</b>{criterion.score5}</span>
                            <span className={chosen === 3 ? 'hit' : undefined}><b>3점</b>{criterion.score3}</span>
                            <span className={chosen === 1 ? 'hit' : undefined}><b>1점</b>{criterion.score1}</span>
                        </div>
                    </div>
                )
            })}
            <div className="crit">
                <label className="field">
                    서술 의견(선택, 2000자까지)
                    <textarea className="input" value={input.comment} maxLength={2000} rows={3} onChange={(event) => onCommentChange(event.target.value)} />
                </label>
            </div>
        </>
    )
}

export default EvaluationTargetForm
