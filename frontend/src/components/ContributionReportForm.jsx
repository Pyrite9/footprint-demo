// 담당 조원이 완료 보고 메모를 쓰는 form입니다. 오른쪽 상세 안에 열립니다. 입력값과 동작은 page에서 props로 받습니다.
function ContributionReportForm({ memo, onMemoChange, onSubmit, reporting }) {
    function handleSubmit(event) {
        event.preventDefault()
        onSubmit()
    }

    return (
        <form onSubmit={handleSubmit} className="sec">
            <label className="field">
                완료 보고 메모(필수, 2000자까지)
                <textarea className="input" value={memo} maxLength={2000} rows={3} onChange={(event) => onMemoChange(event.target.value)} />
            </label>
            <p className="sub small">조원과 교수가 이 메모를 봅니다. 보고한 뒤에는 고칠 수 없습니다.</p>
            <button type="submit" className="btn primary" disabled={reporting || memo.trim() === ''}>완료 보고</button>
        </form>
    )
}

export default ContributionReportForm
