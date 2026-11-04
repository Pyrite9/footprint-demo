import { CONTRIBUTION_STATUS_PILLS, contributionStatusOf, formatDateTime } from '../format'
import ConfirmationPanel from './ConfirmationPanel'
import ContributionReportForm from './ContributionReportForm'

// 오른쪽 상세에 기여 항목 하나를 그립니다. 데이터와 동작은 page에서 props로 받습니다.
// 완료 보고 form은 기록 단계에서 내가 맡은 보고 전 항목에만, 취소 버튼은 기록 단계의 조장에게 보고 전 항목에만 보여 줍니다.
// 완료확인은 보고된 항목에만 있습니다. 새 확인은 기록 단계의 조원이 내가 맡지 않은 항목에만 합니다.
function ContributionDetail({ contribution, currentMemberId, isMember, isRecording, isRecordingLeader, memo, onMemoChange, onReport, onCancel, busy, confirmations, onConfirm, onCancelConfirmation, confirmationBusy }) {
    const status = contributionStatusOf(contribution)
    const beforeReport = contribution.reportedAt === null && contribution.canceledAt === null
    const isMine = contribution.assigneeMemberId === currentMemberId

    return (
        <>
            <div><span className={`pill ${CONTRIBUTION_STATUS_PILLS[status]}`}>{status}</span></div>
            <dl className="props">
                <dt>담당</dt>
                <dd>{contribution.assigneeName}</dd>
                <dt>등록</dt>
                <dd>{formatDateTime(contribution.createdAt)}</dd>
                {contribution.reportedAt !== null && <dt>보고</dt>}
                {contribution.reportedAt !== null && <dd>{formatDateTime(contribution.reportedAt)}</dd>}
                {contribution.canceledAt !== null && <dt>취소</dt>}
                {contribution.canceledAt !== null && <dd>{formatDateTime(contribution.canceledAt)}</dd>}
            </dl>
            {contribution.reportMemo !== null && (
                <div className="sec">
                    <h3>보고 메모</h3>
                    <div className="memo">{contribution.reportMemo}</div>
                </div>
            )}
            {isRecording && beforeReport && isMine && (
                <ContributionReportForm memo={memo} onMemoChange={onMemoChange} onSubmit={onReport} reporting={busy} />
            )}
            {isRecordingLeader && beforeReport && (
                <div><button type="button" className="btn danger" onClick={onCancel} disabled={busy}>항목 취소</button></div>
            )}
            {contribution.reportedAt !== null && (
                <ConfirmationPanel
                    confirmations={confirmations}
                    currentMemberId={currentMemberId}
                    canConfirm={isRecording && isMember && !isMine}
                    canCancel={isRecording && isMember}
                    note={confirmNote(isMember, isRecording, isMine)}
                    onConfirm={onConfirm}
                    onCancel={onCancelConfirmation}
                    busy={confirmationBusy}
                />
            )}
        </>
    )
}

// 완료확인 구역 아래의 안내 문구입니다. 지금 왜 확인할 수 없는지, 또는 확인하면 어떻게 남는지 알려 줍니다.
function confirmNote(isMember, isRecording, isMine) {
    if (!isMember) return '교수는 이력만 봅니다.'
    if (!isRecording) return '평가 단계에서는 완료확인을 남길 수 없습니다.'
    if (isMine) return '담당 조원은 자기 기록을 확인할 수 없습니다.'
    return '취소해도 이력에 남습니다.'
}

export default ContributionDetail
