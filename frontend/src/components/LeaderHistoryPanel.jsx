// 조장 교체 이력을 최근에 바뀐 것부터 그립니다. 데이터는 page에서 props로 받습니다.
function LeaderHistoryPanel({ histories }) {
    return (
        <section className="panel">
            <h2>조장 교체 이력</h2>
            <ul className="item-list">
                {histories.map((history) => (
                    <li key={history.id}>
                        <span>{history.previousLeaderName} → <strong>{history.newLeaderName}</strong></span>
                        <span className="muted">{history.changedAt.replace('T', ' ')}</span>
                    </li>
                ))}
            </ul>
        </section>
    )
}

export default LeaderHistoryPanel
