import { CONTRIBUTION_STATUS_PILLS, contributionStatusOf, formatShortDateTime, initialOf } from '../format'

// 조의 기여 항목을 최근에 등록한 것부터 표로 그립니다. 취소된 항목도 빼지 않고 "취소됨"으로 보여 줍니다. 줄의 항목명을 누르면 오른쪽 상세가 열립니다. 데이터와 동작은 page에서 props로 받습니다.
function ContributionList({ contributions, emptyTitle, selectedId, onSelect }) {
    if (contributions.length === 0) {
        return (
            <section className="card empty">
                <b>{emptyTitle}</b>
                <p className="sub">조장이 항목을 등록하면 여기에 나옵니다.</p>
            </section>
        )
    }

    return (
        <section className="card scroll-x">
            <div className="tr th cols-contribution">
                <span>항목</span>
                <span>담당</span>
                <span>상태</span>
                <span>등록</span>
                <span>보고</span>
            </div>
            {contributions.map((contribution) => {
                const status = contributionStatusOf(contribution)

                return (
                    <div key={contribution.id} className={contribution.id === selectedId ? 'tr body cols-contribution sel' : 'tr body cols-contribution'}>
                        <button type="button" className="cell-btn" onClick={() => onSelect(contribution.id)}>
                            <span className={contribution.canceledAt === null ? undefined : 'struck'}>{contribution.title}</span>
                        </button>
                        <span className="row nowrap">
                            <span className="av sm">{initialOf(contribution.assigneeName)}</span>
                            <span>{contribution.assigneeName}</span>
                        </span>
                        <span><span className={`pill ${CONTRIBUTION_STATUS_PILLS[status]}`}>{status}</span></span>
                        <span className="sub">{formatShortDateTime(contribution.createdAt)}</span>
                        <span className="sub">{contribution.reportedAt === null ? '-' : formatShortDateTime(contribution.reportedAt)}</span>
                    </div>
                )
            })}
        </section>
    )
}

export default ContributionList
