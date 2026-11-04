import RoleAssignForm from './RoleAssignForm'

// 조원 목록을 이름과 역할로 그립니다. 데이터는 page에서 props로 받습니다.
// canAssignRole이 true(조장)이면 조원마다 역할 form을 보여 줍니다. 역할이 없으면 지정, 있으면 변경입니다.
// 역할이 있는 조원에게는 이력 버튼을 보여 줍니다. 역할이 없으면 바뀐 적도 없어서 볼 것이 없습니다.
// canChangeLeader가 true(교수)이면 조장이 아닌 조원에게 조장 교체 버튼을 보여 줍니다.
// roleNameInputs는 { 조 참여 번호: 입력 중인 역할명 } 모양입니다. busy가 true이면 요청이 끝날 때까지 버튼과 입력 칸을 막습니다.
function TeamMemberList({ members, canAssignRole, canChangeLeader, roleNameInputs, onRoleNameChange, onSaveRole, onShowHistory, onChangeLeader, busy }) {
    return (
        <ul className="item-list">
            {members.map((member) => (
                <li key={member.id}>
                    <strong>{member.name}</strong>
                    {member.leader && <span className="muted">조장</span>}
                    {member.roleName === null ? <span className="muted">역할 없음</span> : <span className="badge">{member.roleName}</span>}
                    {/* end: 버튼과 form을 함께 줄의 오른쪽 끝에 붙입니다(index.css). */}
                    <div className="row end">
                        {member.roleName !== null && (
                            <button type="button" className="secondary" onClick={() => onShowHistory(member.id)} disabled={busy}>이력</button>
                        )}
                        {canChangeLeader && !member.leader && (
                            <button type="button" className="secondary" onClick={() => onChangeLeader(member)} disabled={busy}>새 조장으로</button>
                        )}
                        {canAssignRole && (
                            <RoleAssignForm
                                value={roleNameInputs[member.id] ?? ''}
                                onChange={(value) => onRoleNameChange(member.id, value)}
                                onSubmit={() => onSaveRole(member)}
                                submitLabel={member.roleName === null ? '지정' : '변경'}
                                disabled={busy}
                            />
                        )}
                    </div>
                </li>
            ))}
        </ul>
    )
}

export default TeamMemberList
