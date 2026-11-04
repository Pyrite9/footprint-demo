// 기록 하나의 완료확인 이력을 최근 것부터 그립니다. 건수는 세지 않습니다. 목록 줄 안에 열리고, 데이터와 동작은 page에서 props로 받습니다.
// 완료확인 버튼은 canConfirm(이 기록을 확인할 수 있는 조원)이고 내 유효한 확인이 없을 때만 보여 줍니다.
function ConfirmationPanel({ confirmations, currentMemberId, canConfirm, onConfirm, onClose, busy }) {
    const myConfirmation = confirmations.find((confirmation) => confirmation.confirmerMemberId === currentMemberId && confirmation.canceledAt === null)

    return (
        <div className="item-full">
            <h4>완료확인 이력</h4>
            {confirmations.length === 0 ? (
                <p className="muted">아직 완료확인이 없습니다.</p>
            ) : (
                <ul className="item-list">
                    {confirmations.map((confirmation) => (
                        <li key={confirmation.id}>
                            <strong>{confirmation.confirmerName}</strong>
                            <span className="muted">확인 {confirmation.confirmedAt.replace('T', ' ')}</span>
                        </li>
                    ))}
                </ul>
            )}
            <div className="row">
                {canConfirm && myConfirmation === undefined && <button type="button" onClick={onConfirm} disabled={busy}>완료확인</button>}
                <button type="button" className="secondary" onClick={onClose} disabled={busy}>닫기</button>
            </div>
        </div>
    )
}

export default ConfirmationPanel
