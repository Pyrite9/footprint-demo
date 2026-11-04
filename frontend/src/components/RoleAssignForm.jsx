// 조원 한 명의 역할명을 입력해 지정하거나 바꾸는 작은 form입니다. 입력값, 버튼 글자, 동작은 page에서 props로 받습니다.
function RoleAssignForm({ value, onChange, onSubmit, submitLabel, disabled }) {
    function handleSubmit(event) {
        event.preventDefault()
        onSubmit()
    }

    return (
        // row: 입력 칸과 버튼을 가로로 놓습니다(index.css).
        <form onSubmit={handleSubmit} className="row">
            <input value={value} placeholder="역할명" disabled={disabled} onChange={(event) => onChange(event.target.value)} />
            <button type="submit" className="secondary" disabled={disabled}>{submitLabel}</button>
        </form>
    )
}

export default RoleAssignForm
