import { Link } from 'react-router'
import { formatDateTime, formatShortDateTime, isOutsidePeriod, roleColorClass, timelinePercentOf } from '../format'

// 역할 하나의 줄입니다. 그 역할로 올린 파일마다 점을 찍고 마지막 업로드 시점을 함께 보여 줍니다. 몇 개를 올렸는지는 세지 않습니다.
// deliverables: 이 역할의 산출물입니다. 서버가 최신순으로 주므로 첫 번째가 마지막 업로드입니다. 삭제된 파일도 올린 기록이라 빼지 않습니다.
function ProgressLane({ teamId, roleName, people, deliverables, startDate, endDate }) {
    const last = deliverables[0]

    return (
        <div className={`lane ${roleColorClass(roleName)}`}>
            <div className="lane-head">
                <span className="role"><span className="dot" />{roleName}</span>
                <span className="sub small">{people.length > 0 ? people.join(', ') : '지금 맡은 조원 없음'}</span>
            </div>
            <div className="lane-track">
                {/* 뒤에 그린 점이 위로 올라옵니다. 같은 자리에 겹치면 최근 파일이 보이도록 오래된 것부터 그립니다. */}
                {[...deliverables].reverse().map((deliverable) => {
                    const label = `${deliverable.fileName}, ${formatDateTime(deliverable.uploadedAt)}${deliverable.deletedAt === null ? '' : ', 삭제됨'}`
                    return (
                        <Link
                            key={deliverable.id}
                            to={`/teams/${teamId}/deliverables`}
                            state={{ selectedId: deliverable.id }}
                            className={markClass(deliverable, startDate, endDate)}
                            style={{ left: `${timelinePercentOf(deliverable.uploadedAt, startDate, endDate)}%` }}
                            title={label}
                            aria-label={label}
                        />
                    )
                })}
            </div>
            {last === undefined && <p className="sub small">이 역할로 올린 파일이 아직 없습니다.</p>}
            {last !== undefined && (
                <p className="lane-last">
                    <span className="sub">마지막 업로드</span>
                    <b>{formatShortDateTime(last.uploadedAt)}</b>
                    <span className={last.deletedAt === null ? 'lane-file' : 'lane-file struck'}>{last.fileName}</span>
                    {last.deletedAt !== null && <span className="pill gray">삭제됨</span>}
                </p>
            )}
        </div>
    )
}

// 점의 모양 class를 정합니다. 색만으로 나누지 않고 모양으로 나눕니다.
function markClass(deliverable, startDate, endDate) {
    const classes = ['lane-mark']
    if (deliverable.deletedAt !== null) classes.push('deleted')
    if (isOutsidePeriod(deliverable.uploadedAt, startDate, endDate)) classes.push('outside')
    return classes.join(' ')
}

export default ProgressLane
