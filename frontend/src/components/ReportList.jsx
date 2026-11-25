import { formatShortDateTime } from '../format'

// 조의 보고서를 최근에 만든 것부터 표로 그립니다. 줄의 제목을 누르면 그 보고서의 파트가 아래에 열립니다. 데이터와 동작은 page에서 props로 받습니다.
function ReportList({ reports, selectedId, onSelect }) {
    if (reports.length === 0) {
        return (
            <section className="card empty">
                <b>아직 만든 보고서가 없습니다</b>
                <p className="sub">조장이 보고서를 만들면 여기에 나옵니다.</p>
            </section>
        )
    }

    return (
        <section className="card scroll-x">
            <div className="tr th cols-report">
                <span>보고서</span>
                <span>만든 시각</span>
            </div>
            {reports.map((report) => (
                <div key={report.id} className={report.id === selectedId ? 'tr body cols-report sel' : 'tr body cols-report'}>
                    {/* aria-pressed: 화면 읽기 프로그램에 이 보고서가 지금 열려 있는지 알려 줍니다. */}
                    <button type="button" className="cell-btn" aria-pressed={report.id === selectedId} onClick={() => onSelect(report.id)}>
                        {report.title}
                    </button>
                    <span className="sub">{formatShortDateTime(report.createdAt)}</span>
                </div>
            ))}
        </section>
    )
}

export default ReportList
