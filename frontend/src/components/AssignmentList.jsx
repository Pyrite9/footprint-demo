import { Link } from 'react-router'

// 과제 목록을 그립니다. 과제를 누르면 과제 상세 화면으로 갑니다. 데이터는 page에서 props로 받습니다.
function AssignmentList({ assignments }) {
    if (assignments.length === 0) {
        return <p>참여한 과제가 없습니다.</p>
    }

    return (
        <ul className="item-list">
            {assignments.map((assignment) => (
                <li key={assignment.id}>
                    <Link to={`/assignments/${assignment.id}`}>{assignment.title}</Link>
                    <span className="muted">{assignment.startDate} ~ {assignment.endDate}</span>
                </li>
            ))}
        </ul>
    )
}

export default AssignmentList
