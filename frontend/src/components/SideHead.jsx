import { ASSIGNMENT_PHASE_LABELS } from '../constants/assignmentPhases'
import { formatDateTime, PHASE_PILLS } from '../format'

// 메뉴 상자의 머리 부분입니다. 지금 보는 과제의 이름, 단계, 기간, 담당 교수, 평가 시작 시각을 보여 줍니다.
function SideHead({ assignment }) {
    return (
        <div className="side-head">
            <div className="side-title">{assignment.title}</div>
            <div className="side-sub">
                <span className={`pill ${PHASE_PILLS[assignment.phase]}`}>{ASSIGNMENT_PHASE_LABELS[assignment.phase]}</span>
                <span className="side-info">{assignment.startDate} ~ {assignment.endDate}</span>
                <span className="side-info">담당 교수 {assignment.professorName}</span>
                {assignment.evaluationStartedAt && <span className="side-info">평가 시작 {formatDateTime(assignment.evaluationStartedAt)}</span>}
            </div>
        </div>
    )
}

export default SideHead
