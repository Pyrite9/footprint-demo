import { Link } from 'react-router'
import { railLabelOf } from '../format'
import Icon from './Icon'
import LogoMark from './LogoMark'

// 왼쪽 끝의 과제 레일입니다. 로고는 내 과제로, 칸 하나는 과제 하나로 갑니다. 교수에게는 새 과제 버튼도 보여 줍니다.
// 칸에는 과제명을 줄인 두 글자만 넣습니다. 전체 이름은 마우스를 올리면 보입니다(title).
function AssignmentRail({ assignments, currentAssignmentId, isProfessor }) {
    return (
        <div className="rail-col">
            <nav className="rail-in" aria-label="과제">
                <Link to="/" className="tile brand" aria-label="내 과제" title="내 과제"><LogoMark /></Link>
                <span className="rail-sep" />
                {assignments.map((assignment) => (
                    <Link
                        key={assignment.id}
                        to={`/assignments/${assignment.id}`}
                        className={assignment.id === currentAssignmentId ? 'tile on' : 'tile'}
                        aria-label={assignment.title}
                        title={assignment.title}
                    >
                        {railLabelOf(assignment.title)}
                    </Link>
                ))}
                {/* 과제는 교수만 만듭니다. 버튼을 숨기는 것은 화면 편의이고, 실제로 막는 것은 서버의 403입니다. */}
                {isProfessor && <Link to="/assignments/new" className="tile" aria-label="새 과제" title="새 과제"><Icon name="plus" /></Link>}
            </nav>
        </div>
    )
}

export default AssignmentRail
