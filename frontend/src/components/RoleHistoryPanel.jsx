// 조원 한 명의 역할 이력을 최근에 바뀐 것부터 그립니다. 데이터와 닫기 동작은 page에서 props로 받습니다.
function RoleHistoryPanel({ member, histories, onClose }) {
    return (
        <section className="panel">
            <div className="row">
                <h2>{member.name}의 역할 이력</h2>
                <button type="button" className="secondary end" onClick={onClose}>닫기</button>
            </div>
            <p>지금 역할 <span className="badge">{member.roleName}</span></p>
            {histories.length === 0 ? (
                <p className="muted">역할이 바뀐 적이 없습니다.</p>
            ) : (
                <ul className="item-list">
                    {histories.map((history) => (
                        <li key={history.id}>
                            <span className="badge">{history.previousRoleName}</span>
                            <span className="muted">{history.changedAt.replace('T', ' ')}까지</span>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    )
}

export default RoleHistoryPanel
