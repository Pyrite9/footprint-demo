import RoleAssignForm from './RoleAssignForm'

// 조원 목록을 이름과 역할로 그립니다. 데이터는 page에서 props로 받습니다.
// canAssignRole이 true(조장)이면 역할이 없는 조원 옆에 역할 지정 form을 보여 줍니다. 역할이 있는 조원은 바꿀 수 없습니다(역할 변경은 2단계).
// roleNameInputs는 { 조 참여 번호: 입력 중인 역할명 } 모양입니다.
function TeamMemberList({ members, canAssignRole, roleNameInputs, onRoleNameChange, onAssignRole, assigningId }) {
    return (
        <ul className="item-list">
            {members.map((member) => (
                <li key={member.id}>
                    <strong>{member.name}</strong>
                    {member.leader && <span className="muted">조장</span>}
                    {member.roleName === null ? <span className="muted">역할 없음</span> : <span className="badge">{member.roleName}</span>}
                    {canAssignRole && member.roleName === null && (
                        <RoleAssignForm
                            value={roleNameInputs[member.id] ?? ''}
                            onChange={(value) => onRoleNameChange(member.id, value)}
                            onSubmit={() => onAssignRole(member.id)}
                            disabled={assigningId !== null}
                        />
                    )}
                </li>
            ))}
        </ul>
    )
}

export default TeamMemberList
