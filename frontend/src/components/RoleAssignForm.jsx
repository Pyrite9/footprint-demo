// 조원 한 명에게 역할명을 입력해 지정하는 작은 form입니다. 입력값과 동작은 page에서 props로 받습니다.
function RoleAssignForm({ value, onChange, onSubmit, disabled }) {
    function handleSubmit(event) {
        event.preventDefault()
        onSubmit()
    }

    return (
        <form onSubmit={handleSubmit} style={{ display: 'inline' }}>
            {' '}
            <input value={value} placeholder="역할명" onChange={(event) => onChange(event.target.value)} />
            <button type="submit" disabled={disabled}>지정</button>
        </form>
    )
}

export default RoleAssignForm
