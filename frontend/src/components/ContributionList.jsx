// 조의 기여 항목을 최근에 등록한 것부터 그립니다. 데이터는 page에서 props로 받습니다.
function ContributionList({ contributions }) {
    if (contributions.length === 0) {
        return <p>아직 등록한 기여 항목이 없습니다.</p>
    }

    return (
        <ul className="item-list">
            {contributions.map((contribution) => (
                <li key={contribution.id}>
                    <span className="badge">{statusOf(contribution)}</span>
                    <strong>{contribution.title}</strong>
                    <span className="muted">담당 {contribution.assigneeName} · 등록 {contribution.createdAt.replace('T', ' ')}</span>
                </li>
            ))}
        </ul>
    )
}

// 상태 이름을 시각으로 정합니다. 서버에도 state에도 두지 않습니다.
function statusOf(contribution) {
    if (contribution.canceledAt !== null) return '취소됨'
    if (contribution.reportedAt !== null) return '보고됨'
    return '보고 전'
}

export default ContributionList
