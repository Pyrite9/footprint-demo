import { roleColorClass } from '../format'
import RoleAssignForm from './RoleAssignForm'
import RoleHistoryPanel from './RoleHistoryPanel'

// 오른쪽 상세에 조원 한 명을 그립니다. 조장에게는 역할 form을, 교수에게는 조장 교체 버튼을 보여 줍니다. 데이터와 동작은 page에서 props로 받습니다.
function MemberDetail({ member, histories, canAssignRole, canChangeLeader, roleNameInput, onRoleNameChange, onSaveRole, onChangeLeader, busy }) {
    return (
        <>
            <dl className="props">
                <dt>역할</dt>
                <dd className={`role ${roleColorClass(member.roleName)}`}><span className="dot" />{member.roleName ?? '역할 없음'}</dd>
                {member.leader && <dt>조장</dt>}
                {member.leader && <dd>이 조의 조장입니다</dd>}
            </dl>
            {canAssignRole && (
                <RoleAssignForm
                    label={member.roleName === null ? '역할 이름' : '새 역할 이름'}
                    value={roleNameInput}
                    onChange={onRoleNameChange}
                    onSubmit={onSaveRole}
                    disabled={busy}
                />
            )}
            {canChangeLeader && !member.leader && (
                <div className="sec">
                    <h3>조장 교체</h3>
                    <button type="button" className="btn" onClick={onChangeLeader} disabled={busy}>새 조장으로 지정</button>
                </div>
            )}
            <RoleHistoryPanel histories={histories} />
        </>
    )
}

export default MemberDetail
