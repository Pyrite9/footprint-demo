import { useId } from 'react'

// 조장이 보고서 파트를 등록하는 form입니다. 오른쪽 상세 안에 열립니다. 입력값과 등록 동작은 page에서 props로 받습니다.
function ReportPartCreateForm({ title, assigneeId, members, onTitleChange, onAssigneeChange, onSubmit, creating }) {
    // useId: label의 htmlFor와 칸의 id를 잇는 이름을 화면마다 겹치지 않게 만듭니다.
    const titleId = useId()
    const assigneeSelectId = useId()

    // required가 비어 있는 칸과 "고르세요"(값이 빈 글자)를 브라우저가 먼저 막으므로, 여기에 오면 두 칸이 채워져 있습니다.
    function handleSubmit(event) {
        event.preventDefault()
        onSubmit()
    }

    return (
        <form onSubmit={handleSubmit} className="pad-none">
            <div className="field">
                <label htmlFor={titleId}>파트 제목</label>
                <input id={titleId} className="input" value={title} maxLength={255} required placeholder="예: 3. 구현" onChange={(event) => onTitleChange(event.target.value)} />
            </div>
            <div className="field">
                <label htmlFor={assigneeSelectId}>담당 조원</label>
                <select id={assigneeSelectId} className="input" value={assigneeId} required onChange={(event) => onAssigneeChange(event.target.value)}>
                    <option value="">고르세요</option>
                    {members.map((member) => (
                        <option key={member.id} value={member.id}>{member.name}</option>
                    ))}
                </select>
            </div>
            <p className="sub small">등록한 파트는 고칠 수 없습니다. 바꾸려면 취소하고 다시 등록합니다. 다시 등록한 파트는 맨 뒤에 붙습니다.</p>
            <button type="submit" className="btn primary" disabled={creating}>파트 등록</button>
        </form>
    )
}

export default ReportPartCreateForm
