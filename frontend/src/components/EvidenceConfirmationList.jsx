import { formatShortDateTime, initialOf } from '../format'

// 근거 패널의 완료확인 한 구역입니다. 취소된 확인도 빼지 않고 "취소됨"으로 보여 줍니다. 건수는 세지 않습니다.
// whoOf: 줄 앞에 보여 줄 사람 이름을 고르는 함수입니다. 받은 확인은 확인한 조원, 남긴 확인은 기록의 주인입니다.
function EvidenceConfirmationList({ title, emptyText, confirmations, whoOf, onSelectDeliverable, onSelectContribution }) {
    return (
        <div className="sec">
            <h3>{title}</h3>
            {confirmations.length === 0 && <p className="sub">{emptyText}</p>}
            {confirmations.map((confirmation) => (
                <div key={confirmation.id} className="log">
                    <span className="av sm">{initialOf(whoOf(confirmation))}</span>
                    <span className="log-text">
                        {whoOf(confirmation)}
                        {confirmation.deliverable !== null && (
                            <button type="button" className="cell-btn" onClick={() => onSelectDeliverable(confirmation.deliverable)}>{confirmation.deliverable.fileName}</button>
                        )}
                        {confirmation.contribution !== null && (
                            <button type="button" className="cell-btn" onClick={() => onSelectContribution(confirmation.contribution)}>{confirmation.contribution.title}</button>
                        )}
                        {confirmation.canceledAt !== null && <span className="pill gray">취소됨</span>}
                    </span>
                    <time>{formatShortDateTime(confirmation.confirmedAt)}</time>
                </div>
            ))}
        </div>
    )
}

export default EvidenceConfirmationList
