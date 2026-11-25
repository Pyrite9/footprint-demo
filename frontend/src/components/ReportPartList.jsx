import { formatShortDateTime, initialOf } from '../format'

// 보고서의 파트를 먼저 등록한 것부터 표로 그립니다. 합본도 이 순서입니다. 취소된 파트도 빼지 않고 "취소됨"으로 보여 줍니다.
// 줄의 파트 제목을 누르면 오른쪽 상세가 열립니다. 데이터와 동작은 page에서 props로 받습니다.
function ReportPartList({ parts, selectedId, onSelect }) {
    if (parts.length === 0) {
        return (
            <section className="card empty">
                <b>아직 나눈 파트가 없습니다</b>
                <p className="sub">조장이 파트와 담당을 정하면 여기에 나옵니다.</p>
            </section>
        )
    }

    return (
        <section className="card scroll-x">
            <div className="tr th cols-report-part">
                <span>파트</span>
                <span>담당</span>
                <span>상태</span>
                <span>등록</span>
            </div>
            {parts.map((part) => (
                <div key={part.id} className={part.id === selectedId ? 'tr body cols-report-part sel' : 'tr body cols-report-part'}>
                    <button type="button" className="cell-btn" onClick={() => onSelect(part.id)}>
                        <span className={part.canceledAt === null ? undefined : 'struck'}>{part.title}</span>
                    </button>
                    <span className="row nowrap">
                        <span className="av sm">{initialOf(part.assigneeName)}</span>
                        <span>{part.assigneeName}</span>
                    </span>
                    <span>{part.canceledAt === null ? '-' : <span className="pill gray">취소됨</span>}</span>
                    <span className="sub">{formatShortDateTime(part.createdAt)}</span>
                </div>
            ))}
        </section>
    )
}

export default ReportPartList
