import { initialOf, roleColorClass } from '../format'

// 조원 목록을 이름과 역할로 그립니다. 줄의 버튼을 누르면 오른쪽 상세가 열립니다. 데이터와 동작은 page에서 props로 받습니다.
function TeamMemberList({ members, selectedId, onSelect, canAssignRole, canChangeLeader }) {
    return (
        <section className="card scroll-x">
            <div className="tr th cols-member">
                <span>이름</span>
                <span>역할</span>
                <span />
            </div>
            {members.map((member) => (
                <div key={member.id} className={member.id === selectedId ? 'tr body cols-member sel' : 'tr body cols-member'}>
                    <span className="row nowrap">
                        <span className="av">{initialOf(member.name)}</span>
                        <b>{member.name}</b>
                        {member.leader && <span className="pill blue">조장</span>}
                    </span>
                    <span className={`role ${roleColorClass(member.roleName)}`}>
                        <span className="dot" />{member.roleName ?? '역할 없음'}
                    </span>
                    <span>
                        <button type="button" className="btn" onClick={() => onSelect(member.id)}>{actionLabel(member, canAssignRole, canChangeLeader)}</button>
                    </span>
                </div>
            ))}
        </section>
    )
}

// 줄 버튼의 글자입니다. 그 조원의 상세에서 할 수 있는 일을 보여 줍니다.
function actionLabel(member, canAssignRole, canChangeLeader) {
    if (canAssignRole) return member.roleName === null ? '역할 지정' : '역할 변경'
    if (canChangeLeader && !member.leader) return '조장 교체'
    return '이력 보기'
}

export default TeamMemberList
