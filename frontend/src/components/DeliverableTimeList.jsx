import { formatFileSize, formatShortDateTime, roleColorClass, sequenceOf } from '../format'

// 조의 산출물 전체를 최신순 한 표로 그립니다. 줄의 파일 이름을 누르면 오른쪽 상세가 열립니다. 데이터와 동작은 page에서 props로 받습니다.
function DeliverableTimeList({ deliverables, selectedId, onSelect }) {
    // id가 클수록 나중에 올린 것입니다. sort는 원래 배열을 바꾸므로 복사한 배열을 정렬합니다.
    const rows = [...deliverables].sort((a, b) => b.id - a.id)

    return (
        <section className="card scroll-x">
            <div className="tr th cols-time">
                <span>역할</span>
                <span>파일</span>
                <span>올린 사람</span>
                <span>올린 시각</span>
                <span>크기</span>
                <span>상태</span>
            </div>
            {rows.map((deliverable) => (
                <div key={deliverable.id} className={deliverable.id === selectedId ? 'tr body cols-time sel' : 'tr body cols-time'}>
                    <span className={`role ${roleColorClass(deliverable.roleName)}`}>
                        <span className="dot" />{deliverable.roleName} {sequenceOf(deliverables, deliverable)}번째
                    </span>
                    <button type="button" className="cell-btn" onClick={() => onSelect(deliverable.id)}>
                        <span className={deliverable.deletedAt === null ? undefined : 'struck'}>{deliverable.fileName}</span>
                    </button>
                    <span>{deliverable.uploaderName}</span>
                    <span className="sub">{formatShortDateTime(deliverable.uploadedAt)}</span>
                    <span className="sub">{formatFileSize(deliverable.fileSize)}</span>
                    <span>
                        {deliverable.deletedAt !== null && <span className="pill gray">삭제됨</span>}
                    </span>
                </div>
            ))}
        </section>
    )
}

export default DeliverableTimeList
