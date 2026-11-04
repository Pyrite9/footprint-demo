import { CONTRIBUTION_STATUS_PILLS, contributionStatusOf, formatShortDateTime, roleColorClass, sequenceOf } from '../format'
import EvidenceConfirmationList from './EvidenceConfirmationList'

// 근거 패널의 본문입니다. 한 조원의 기록을 종류별로 그립니다. 건수는 세지 않습니다. 기록 이름을 누르면 그 기록을 엽니다. 데이터와 동작은 page에서 props로 받습니다.
// teamDeliverables: 역할별 순번을 계산할 조 전체의 산출물입니다. 순번은 조원마다가 아니라 역할마다 이어집니다.
function EvidencePanel({ evidence, teamDeliverables, onSelectDeliverable, onSelectContribution }) {
    return (
        <>
            <div className="sec">
                <h3>올린 산출물</h3>
                {evidence.deliverables.length === 0 && <p className="sub">올린 산출물이 없습니다.</p>}
                {evidence.deliverables.map((deliverable) => (
                    <div key={deliverable.id} className="log">
                        <span className="log-text">
                            <button type="button" className="cell-btn" onClick={() => onSelectDeliverable(deliverable)}>
                                <span className={deliverable.deletedAt === null ? undefined : 'struck'}>{deliverable.fileName}</span>
                            </button>
                            <span className={`role ${roleColorClass(deliverable.roleName)}`}>
                                <span className="dot" />{deliverable.roleName} {sequenceOf(teamDeliverables, deliverable)}번째
                            </span>
                            {deliverable.deletedAt !== null && <span className="pill gray">삭제됨</span>}
                        </span>
                        <time>{formatShortDateTime(deliverable.uploadedAt)}</time>
                    </div>
                ))}
            </div>
            <div className="sec">
                <h3>맡은 기여 항목</h3>
                {evidence.contributions.length === 0 && <p className="sub">맡은 기여 항목이 없습니다.</p>}
                {evidence.contributions.map((contribution) => {
                    const status = contributionStatusOf(contribution)

                    return (
                        <div key={contribution.id}>
                            <div className="log">
                                <span className="log-text">
                                    <button type="button" className="cell-btn" onClick={() => onSelectContribution(contribution)}>
                                        <span className={contribution.canceledAt === null ? undefined : 'struck'}>{contribution.title}</span>
                                    </button>
                                    <span className={`pill ${CONTRIBUTION_STATUS_PILLS[status]}`}>{status}</span>
                                </span>
                                <time>{formatShortDateTime(contribution.createdAt)}</time>
                            </div>
                            {contribution.reportMemo !== null && <div className="memo">{contribution.reportMemo}</div>}
                        </div>
                    )
                })}
            </div>
            <EvidenceConfirmationList
                title="이 조원의 기록에 남은 완료확인"
                emptyText="다른 조원이 남긴 완료확인이 없습니다."
                confirmations={evidence.receivedConfirmations}
                whoOf={(confirmation) => confirmation.confirmerName}
                onSelectDeliverable={onSelectDeliverable}
                onSelectContribution={onSelectContribution}
            />
            <EvidenceConfirmationList
                title="이 조원이 남긴 완료확인"
                emptyText="다른 조원의 기록에 남긴 완료확인이 없습니다."
                confirmations={evidence.givenConfirmations}
                whoOf={(confirmation) => confirmation.deliverable?.uploaderName ?? confirmation.contribution.assigneeName}
                onSelectDeliverable={onSelectDeliverable}
                onSelectContribution={onSelectContribution}
            />
        </>
    )
}

export default EvidencePanel
