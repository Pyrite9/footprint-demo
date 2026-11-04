import { formatShortDateTime, initialOf } from '../format'

// 조원별 동료평가 제출 여부를 그립니다. 몇 명이 제출했는지는 세지 않습니다. 데이터는 page에서 props로 받습니다.
function EvaluationSubmissionList({ submissions }) {
    return (
        <section className="card group">
            <header className="group-h">
                <h2>제출 현황</h2>
            </header>
            {submissions.map((submission) => (
                <div key={submission.teamMemberId} className="log record-row">
                    <span className="av sm">{initialOf(submission.name)}</span>
                    <span className="log-text record-text">
                        <b>{submission.name}</b>
                        {submission.submitted
                            ? <span className="pill green">제출함</span>
                            : <span className="pill gray">미제출</span>}
                    </span>
                    {submission.submitted && <time>{formatShortDateTime(submission.submittedAt)}</time>}
                </div>
            ))}
        </section>
    )
}

export default EvaluationSubmissionList
