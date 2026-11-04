import { formatDateTime } from '../format'

// 조장 교체 이력을 최근에 바뀐 것부터 그립니다. 데이터는 page에서 props로 받습니다.
function LeaderHistoryPanel({ histories }) {
    return (
        <section className="card pad">
            <h2 className="title">조장 교체 이력</h2>
            {histories.map((history) => (
                <div key={history.id} className="log">
                    <span className="log-text">{history.previousLeaderName} → <b>{history.newLeaderName}</b></span>
                    <time>{formatDateTime(history.changedAt)}</time>
                </div>
            ))}
        </section>
    )
}

export default LeaderHistoryPanel
