import { Link } from 'react-router'
import { formatShortDateTime, initialOf, RECORD_TYPE_LABELS, roleColorClass } from '../format'

// 기록 한 줄입니다. 누가, 무엇을, 언제 했는지 있는 그대로 그립니다. 파일이나 항목 이름을 누르면 그 화면에서 상세가 열립니다.
function RecordRow({ teamId, record }) {
    return (
        <div className="log record-row">
            {record.memberName !== null && <span className="av sm">{initialOf(record.memberName)}</span>}
            <span className="log-text record-text">
                {record.memberName !== null && <b>{record.memberName}</b>}
                <span className="sub">{RECORD_TYPE_LABELS[record.type]}</span>
                {record.type === 'LEADER_CHANGED' && <span>이전 조장 {record.targetMemberName}</span>}
                {record.type.startsWith('CONFIRMATION') && <span>{record.targetMemberName}의 기록</span>}
                <RecordTitle teamId={teamId} record={record} />
                {record.previousRoleName !== null && <span className="sub">{record.previousRoleName} →</span>}
                {record.roleName !== null && (
                    <span className={`role ${roleColorClass(record.roleName)}`}><span className="dot" />{record.roleName}</span>
                )}
            </span>
            <time>{formatShortDateTime(record.occurredAt)}</time>
        </div>
    )
}

// 파일이나 항목 이름입니다. 조 홈의 최근 산출물과 같이 state로 번호를 넘겨 그 화면에서 상세를 엽니다.
function RecordTitle({ teamId, record }) {
    if (record.deliverableId !== null) {
        return <Link to={`/teams/${teamId}/deliverables`} state={{ selectedId: record.deliverableId }} className="record-link">{record.title}</Link>
    }
    if (record.contributionId !== null) {
        return <Link to={`/teams/${teamId}/contributions`} state={{ selectedId: record.contributionId }} className="record-link">{record.title}</Link>
    }
    return null
}

export default RecordRow
