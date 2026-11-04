import { formatDateTime, initialOf } from '../format'

// 기록 하나의 완료확인을 그립니다. 위 구역은 지금 유효한 확인, 아래 구역은 취소가 있었을 때의 전체 이력입니다. 건수는 세지 않습니다. 데이터와 동작은 page에서 props로 받습니다.
// 완료확인 버튼은 canConfirm(이 기록을 확인할 수 있는 조원)이고 내 유효한 확인이 없을 때만, 확인 취소 버튼은 canCancel(기록 단계의 조원)이고 내 유효한 확인이 있을 때만 보여 줍니다.
// note: 버튼 아래의 안내 문구입니다.
function ConfirmationPanel({ confirmations, currentMemberId, canConfirm, canCancel, note, onConfirm, onCancel, busy }) {
    const activeConfirmations = confirmations.filter((confirmation) => confirmation.canceledAt === null)
    const myConfirmation = activeConfirmations.find((confirmation) => confirmation.confirmerMemberId === currentMemberId)
    const hasCanceled = activeConfirmations.length < confirmations.length

    return (
        <>
            <div className="sec">
                <h3>완료확인</h3>
                {activeConfirmations.length === 0 && <p className="sub">아직 완료확인이 없습니다.</p>}
                {activeConfirmations.map((confirmation) => (
                    <div key={confirmation.id} className="log">
                        <span className="av sm">{initialOf(confirmation.confirmerName)}</span>
                        <span className="log-text">{confirmation.confirmerName}</span>
                        <time>{formatDateTime(confirmation.confirmedAt)}</time>
                    </div>
                ))}
                {canConfirm && myConfirmation === undefined && <button type="button" className="btn primary" onClick={onConfirm} disabled={busy}>완료확인</button>}
                {canCancel && myConfirmation !== undefined && <button type="button" className="btn" onClick={() => onCancel(myConfirmation)} disabled={busy}>확인 취소</button>}
                <p className="sub small">{note}</p>
            </div>
            {hasCanceled && (
                <div className="sec">
                    <h3>이력</h3>
                    {confirmations.map((confirmation) => (
                        <div key={confirmation.id} className="log">
                            <span className="av sm">{initialOf(confirmation.confirmerName)}</span>
                            <span className="log-text">
                                {confirmation.confirmerName}
                                {confirmation.canceledAt !== null && <span className="pill gray">취소됨</span>}
                            </span>
                            <time>
                                확인 {formatDateTime(confirmation.confirmedAt)}
                                {confirmation.canceledAt !== null && <><br />취소 {formatDateTime(confirmation.canceledAt)}</>}
                            </time>
                        </div>
                    ))}
                </div>
            )}
        </>
    )
}

export default ConfirmationPanel
