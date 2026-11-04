import { Link } from 'react-router'
import { formatShortDateTime, roleColorClass, sequenceOf } from '../format'

// 조 홈에 보여 줄 최근 산출물 개수입니다.
const RECENT_COUNT = 5

// 조 홈의 최근 산출물을 최신순으로 그립니다. 파일을 누르면 산출물 화면에서 그 파일의 상세가 열립니다. 데이터는 page에서 props로 받습니다.
function RecentDeliverableList({ teamId, deliverables }) {
    // id가 클수록 나중에 올린 것입니다. sort는 원래 배열을 바꾸므로 복사한 배열을 정렬합니다.
    const recent = [...deliverables].sort((a, b) => b.id - a.id).slice(0, RECENT_COUNT)

    return (
        <section className="card group">
            <header className="group-h">
                <h2>최근 산출물</h2>
                <Link to={`/teams/${teamId}/deliverables`} className="btn">산출물 전체</Link>
            </header>
            {recent.length === 0 && (
                <div className="empty">
                    <b>아직 올린 파일이 없습니다</b>
                    <p className="sub">역할이 정해진 조원이 올리면 여기에 나옵니다.</p>
                </div>
            )}
            {recent.length > 0 && (
                <div className="scroll-x">
                    {recent.map((deliverable) => (
                        <div key={deliverable.id} className="tr body cols-recent">
                            <span className={`role ${roleColorClass(deliverable.roleName)}`}>
                                <span className="dot" />{deliverable.roleName} {sequenceOf(deliverables, deliverable)}번째
                            </span>
                            <Link to={`/teams/${teamId}/deliverables`} state={{ selectedId: deliverable.id }} className="cell-btn">
                                <span className={deliverable.deletedAt === null ? undefined : 'struck'}>{deliverable.fileName}</span>
                            </Link>
                            <span>{deliverable.uploaderName}</span>
                            <span className="sub">{formatShortDateTime(deliverable.uploadedAt)}</span>
                        </div>
                    ))}
                </div>
            )}
        </section>
    )
}

export default RecentDeliverableList
