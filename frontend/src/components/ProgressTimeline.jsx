import { roleNamesOf } from '../format'
import ProgressLane from './ProgressLane'

// 과제 기간을 가로축으로 두고 역할마다 한 줄씩 그립니다. 데이터는 page에서 props로 받습니다.
function ProgressTimeline({ team, members, deliverables }) {
    const roleNames = roleNamesOf(members, deliverables)

    if (roleNames.length === 0) {
        return (
            <section className="card empty">
                <b>아직 정해진 역할이 없습니다</b>
                <p className="sub">조장이 역할을 정하면 역할마다 줄이 생깁니다.</p>
            </section>
        )
    }

    return (
        <section className="card timeline">
            <div className="timeline-period">
                <span>{team.startDate}</span>
                <span>{team.endDate}</span>
            </div>
            {roleNames.map((roleName) => (
                <ProgressLane
                    key={roleName}
                    teamId={team.id}
                    roleName={roleName}
                    people={members.filter((member) => member.roleName === roleName).map((member) => member.name)}
                    deliverables={deliverables.filter((deliverable) => deliverable.roleName === roleName)}
                    startDate={team.startDate}
                    endDate={team.endDate}
                />
            ))}
            <p className="sub small timeline-note">점을 누르면 그 파일을 엽니다. 속이 빈 점은 삭제된 파일이고, 네모는 과제 기간 밖에 올린 파일로 줄의 양 끝에 놓입니다.</p>
        </section>
    )
}

export default ProgressTimeline
