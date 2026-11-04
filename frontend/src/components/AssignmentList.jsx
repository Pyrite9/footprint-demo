import { Link } from 'react-router'
import { ASSIGNMENT_PHASE_LABELS } from '../constants/assignmentPhases'
import { PHASE_PILLS } from '../format'

// 과제 목록을 상자로 그립니다. 과제를 누르면 그 과제로 갑니다. 데이터는 page에서 props로 받습니다.
function AssignmentList({ assignments, isProfessor }) {
    if (assignments.length === 0) {
        return (
            <section className="card empty">
                <b>아직 볼 수 있는 과제가 없습니다</b>
                <p className="sub">{isProfessor ? '새 과제를 만들면 여기에 나옵니다.' : '조장이 보낸 초대 링크를 열면 그 조에 합류합니다.'}</p>
            </section>
        )
    }

    return (
        <div className="grid3">
            {assignments.map((assignment) => (
                <Link key={assignment.id} to={`/assignments/${assignment.id}`} className="acard">
                    <span className="row between">
                        <b>{assignment.title}</b>
                        <span className={`pill ${PHASE_PILLS[assignment.phase]}`}>{ASSIGNMENT_PHASE_LABELS[assignment.phase]}</span>
                    </span>
                    <span className="sub">{assignment.startDate} ~ {assignment.endDate}</span>
                </Link>
            ))}
        </div>
    )
}

export default AssignmentList
