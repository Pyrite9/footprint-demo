// 조원 한 명의 역할명을 입력해 지정하거나 바꾸는 작은 form입니다. 입력값, 칸 이름, 동작은 page에서 props로 받습니다.
function RoleAssignForm({ label, value, onChange, onSubmit, disabled }) {
    function handleSubmit(event) {
        event.preventDefault()
        onSubmit()
    }

    return (
        <form onSubmit={handleSubmit} className="sec">
            <label className="field">
                {label}
                <input className="input" value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)} />
            </label>
            <p className="sub small">이미 올린 산출물은 올릴 때의 역할로 남습니다.</p>
            <button type="submit" className="btn primary" disabled={disabled}>저장</button>
        </form>
    )
}

export default RoleAssignForm
