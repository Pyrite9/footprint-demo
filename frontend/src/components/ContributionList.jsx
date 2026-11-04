import ConfirmationPanel from './ConfirmationPanel'
import ContributionReportForm from './ContributionReportForm'

// 조의 기여 항목을 최근에 등록한 것부터 그립니다. 취소된 항목도 빼지 않고 "취소됨"으로 보여 줍니다. 데이터와 동작은 page에서 props로 받습니다.
// 완료 보고 버튼은 canReport(기록 단계)이고 내가 맡은 보고 전 항목에만, 취소 버튼은 canCancel(기록 단계의 조장)이고 보고 전 항목에만 보여 줍니다.
// 완료확인 이력은 보고된 항목에서만 엽니다. 보고 전 항목과 취소된 항목에는 확인이 없습니다. 새 확인은 canConfirm(기록 단계의 조원)이고 내가 맡지 않은 항목에만 합니다.
function ContributionList({ contributions, currentMemberId, canReport, canCancel, reportTargetId, memo, onStartReport, onMemoChange, onSubmitReport, onCloseReport, onCancel, busy, canConfirm, confirmationTargetId, confirmations, onOpenConfirmations, onCloseConfirmations, onConfirm, confirmationBusy }) {
    if (contributions.length === 0) {
        return <p>아직 등록한 기여 항목이 없습니다.</p>
    }

    return (
        <ul className="item-list">
            {contributions.map((contribution) => {
                const beforeReport = isBeforeReport(contribution)
                const showReport = canReport && beforeReport && contribution.assigneeMemberId === currentMemberId && reportTargetId !== contribution.id
                const showCancel = canCancel && beforeReport
                const showConfirmations = contribution.reportedAt !== null && confirmationTargetId !== contribution.id

                return (
                    <li key={contribution.id}>
                        <span className="badge">{statusOf(contribution)}</span>
                        {contribution.canceledAt === null ? <strong>{contribution.title}</strong> : <span className="muted">{contribution.title}</span>}
                        <span className="muted">
                            담당 {contribution.assigneeName} · 등록 {contribution.createdAt.replace('T', ' ')}
                            {contribution.reportedAt !== null && ` · 보고 ${contribution.reportedAt.replace('T', ' ')}`}
                            {contribution.canceledAt !== null && ` · 취소 ${contribution.canceledAt.replace('T', ' ')}`}
                        </span>
                        {(showReport || showCancel || showConfirmations) && (
                            <div className="row end">
                                {showReport && <button type="button" className="secondary" onClick={() => onStartReport(contribution)} disabled={busy}>완료 보고</button>}
                                {showCancel && <button type="button" className="secondary" onClick={() => onCancel(contribution)} disabled={busy}>취소</button>}
                                {showConfirmations && <button type="button" className="secondary" onClick={() => onOpenConfirmations(contribution)} disabled={confirmationBusy}>완료확인 이력</button>}
                            </div>
                        )}
                        {contribution.reportMemo !== null && <p className="item-full">{contribution.reportMemo}</p>}
                        {reportTargetId === contribution.id && (
                            <ContributionReportForm
                                memo={memo}
                                onMemoChange={onMemoChange}
                                onSubmit={onSubmitReport}
                                onClose={onCloseReport}
                                reporting={busy}
                            />
                        )}
                        {confirmationTargetId === contribution.id && (
                            <ConfirmationPanel
                                confirmations={confirmations}
                                currentMemberId={currentMemberId}
                                canConfirm={canConfirm && contribution.assigneeMemberId !== currentMemberId}
                                onConfirm={onConfirm}
                                onClose={onCloseConfirmations}
                                busy={confirmationBusy}
                            />
                        )}
                    </li>
                )
            })}
        </ul>
    )
}

// 보고도 취소도 하지 않은 항목이면 true입니다.
function isBeforeReport(contribution) {
    return contribution.reportedAt === null && contribution.canceledAt === null
}

// 상태 이름을 시각으로 정합니다. 서버에도 state에도 두지 않습니다.
function statusOf(contribution) {
    if (contribution.canceledAt !== null) return '취소됨'
    if (contribution.reportedAt !== null) return '보고됨'
    return '보고 전'
}

export default ContributionList
