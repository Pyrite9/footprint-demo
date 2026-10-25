import { EVALUATION_NOTICES } from '../constants/peerEvaluation'

// 동료평가 화면 위쪽에 고정하는 안내 문구입니다. 입력 중에도, 제출한 뒤에도 항상 보여 줍니다.
function EvaluationNotice() {
    return (
        <ul>
            {EVALUATION_NOTICES.map((notice) => (
                <li key={notice}><strong>{notice}</strong></li>
            ))}
        </ul>
    )
}

export default EvaluationNotice
