import { formatDateTime } from '../format'

// 조원 한 명의 역할 이력을 최근에 바뀐 것부터 그립니다. 데이터는 page에서 props로 받습니다.
function RoleHistoryPanel({ histories }) {
    return (
        <div className="sec">
            <h3>역할 이력</h3>
            {histories.length === 0 && <p className="sub">바뀐 적이 없습니다.</p>}
            {histories.map((history) => (
                <div key={history.id} className="log">
                    <span className="log-text">{history.previousRoleName}</span>
                    <time>{formatDateTime(history.changedAt)}까지</time>
                </div>
            ))}
        </div>
    )
}

export default RoleHistoryPanel
