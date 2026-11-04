import ContributionReportForm from './ContributionReportForm'

// 조의 기여 항목을 최근에 등록한 것부터 그립니다. 데이터와 동작은 page에서 props로 받습니다.
// 완료 보고 버튼은 canReport(기록 단계)이고 내가 맡은 보고 전 항목에만 보여 줍니다.
function ContributionList({ contributions, currentMemberId, canReport, reportTargetId, memo, onStartReport, onMemoChange, onSubmitReport, onCloseReport, reporting }) {
    if (contributions.length === 0) {
        return <p>아직 등록한 기여 항목이 없습니다.</p>
    }

    return (
        <ul className="item-list">
            {contributions.map((contribution) => (
                <li key={contribution.id}>
                    <span className="badge">{statusOf(contribution)}</span>
                    <strong>{contribution.title}</strong>
                    <span className="muted">
                        담당 {contribution.assigneeName} · 등록 {contribution.createdAt.replace('T', ' ')}
                        {contribution.reportedAt !== null && ` · 보고 ${contribution.reportedAt.replace('T', ' ')}`}
                    </span>
                    {canReport && contribution.assigneeMemberId === currentMemberId && isBeforeReport(contribution) && reportTargetId !== contribution.id && (
                        <div className="row end">
                            <button type="button" className="secondary" onClick={() => onStartReport(contribution)} disabled={reporting}>완료 보고</button>
                        </div>
                    )}
                    {contribution.reportMemo !== null && <p className="item-full">{contribution.reportMemo}</p>}
                    {reportTargetId === contribution.id && (
                        <ContributionReportForm
                            memo={memo}
                            onMemoChange={onMemoChange}
                            onSubmit={onSubmitReport}
                            onClose={onCloseReport}
                            reporting={reporting}
                        />
                    )}
                </li>
            ))}
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
