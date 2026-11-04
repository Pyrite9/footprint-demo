import { formatDateTime, formatFileSize, roleColorClass, sequenceOf } from '../format'
import ConfirmationPanel from './ConfirmationPanel'
import Icon from './Icon'

// 오른쪽 상세에 산출물 하나를 그립니다. 데이터와 동작은 page에서 props로 받습니다.
// 삭제된 산출물은 파일이 없어서 내려받기 버튼이 없습니다. 삭제 버튼은 기록 단계에서 내가 올린 산출물에만 보여 줍니다.
// 완료확인 이력은 삭제된 산출물에도 남습니다. 새 확인은 기록 단계의 조원이 내가 올리지 않은, 삭제되지 않은 산출물에만 합니다. 내 확인 취소는 삭제된 산출물에서도 합니다.
function DeliverableDetail({ deliverable, deliverables, currentMemberId, isMember, isRecording, onDownload, downloading, onDelete, confirmations, onConfirm, onCancelConfirmation, confirmationBusy }) {
    const isDeleted = deliverable.deletedAt !== null
    const isMine = deliverable.uploaderMemberId === currentMemberId

    return (
        <>
            {isDeleted && <div><span className="pill gray">삭제됨 · {formatDateTime(deliverable.deletedAt)}</span></div>}
            <dl className="props">
                <dt>역할</dt>
                <dd className={`role ${roleColorClass(deliverable.roleName)}`}>
                    <span className="dot" />{deliverable.roleName} {sequenceOf(deliverables, deliverable)}번째
                </dd>
                <dt>올린 사람</dt>
                <dd>{deliverable.uploaderName}</dd>
                <dt>올린 시각</dt>
                <dd>{formatDateTime(deliverable.uploadedAt)}</dd>
                <dt>크기</dt>
                <dd>{formatFileSize(deliverable.fileSize)}</dd>
            </dl>
            {!isDeleted && (
                <div className="row">
                    <button type="button" className="btn" onClick={() => onDownload(deliverable)} disabled={downloading}><Icon name="download" />내려받기</button>
                    {isRecording && isMine && <button type="button" className="btn danger push-right" onClick={onDelete}>삭제</button>}
                </div>
            )}
            <ConfirmationPanel
                confirmations={confirmations}
                currentMemberId={currentMemberId}
                canConfirm={isRecording && isMember && !isDeleted && !isMine}
                canCancel={isRecording && isMember}
                note={confirmNote(isMember, isRecording, isDeleted, isMine)}
                onConfirm={onConfirm}
                onCancel={onCancelConfirmation}
                busy={confirmationBusy}
            />
        </>
    )
}

// 완료확인 구역 아래의 안내 문구입니다. 지금 왜 확인할 수 없는지, 또는 확인하면 어떻게 남는지 알려 줍니다.
function confirmNote(isMember, isRecording, isDeleted, isMine) {
    if (!isMember) return '교수는 이력만 봅니다.'
    if (!isRecording) return '평가 단계에서는 완료확인을 남길 수 없습니다.'
    if (isDeleted) return '삭제된 산출물에는 새로 완료확인할 수 없습니다.'
    if (isMine) return '올린 조원은 자기 기록을 확인할 수 없습니다.'
    return '취소해도 이력에 남습니다.'
}

export default DeliverableDetail
