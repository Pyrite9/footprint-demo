import { EVALUATION_CRITERIA } from '../constants/peerEvaluation'

// 제출 전에 대상 조원 전원의 입력 내용을 한 표로 보여 줍니다. 줄은 항목, 칸은 조원입니다. 점수를 더하거나 평균 내지 않습니다.
function EvaluationReview({ targets, inputs }) {
    return (
        // table-scroll: 조원이 많아 표가 화면보다 넓으면 표만 가로로 밀어 봅니다(index.css).
        <div className="table-scroll">
            <table>
                <thead>
                    <tr>
                        <th>항목</th>
                        {targets.map((target) => <th key={target.id}>{target.name}</th>)}
                    </tr>
                </thead>
                <tbody>
                    {EVALUATION_CRITERIA.map((criterion) => (
                        <tr key={criterion.key}>
                            <td>{criterion.name}</td>
                            {targets.map((target) => <td key={target.id}>{inputs[target.id][criterion.key]}점</td>)}
                        </tr>
                    ))}
                    <tr>
                        <td>서술 의견</td>
                        {targets.map((target) => (
                            <td key={target.id}>{inputs[target.id].comment.trim() === '' ? '(없음)' : inputs[target.id].comment}</td>
                        ))}
                    </tr>
                </tbody>
            </table>
        </div>
    )
}

export default EvaluationReview
