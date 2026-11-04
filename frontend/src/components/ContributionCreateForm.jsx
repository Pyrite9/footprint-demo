// 조장이 기여 항목을 등록하는 form입니다. 입력값과 등록 동작은 page에서 props로 받습니다.
function ContributionCreateForm({ title, assigneeId, members, onTitleChange, onAssigneeChange, onSubmit, creating }) {
    function handleSubmit(event) {
        event.preventDefault()
        onSubmit()
    }

    return (
        <form onSubmit={handleSubmit} className="panel">
            <h2>기여 항목 등록</h2>
            <p className="muted">파일이 남지 않는 일(회의 진행, 자료 취합 등)을 기록합니다. 등록한 항목은 고칠 수 없습니다. 보고 전이면 취소하고 다시 등록합니다.</p>
            <p>
                <label className="field">
                    항목명
                    <input value={title} maxLength={255} onChange={(event) => onTitleChange(event.target.value)} />
                </label>
            </p>
            <p>
                <label className="field">
                    담당 조원
                    <select value={assigneeId} onChange={(event) => onAssigneeChange(event.target.value)}>
                        <option value="">고르세요</option>
                        {members.map((member) => (
                            <option key={member.id} value={member.id}>{member.name}</option>
                        ))}
                    </select>
                </label>
            </p>
            <button type="submit" disabled={creating}>등록</button>
        </form>
    )
}

export default ContributionCreateForm
