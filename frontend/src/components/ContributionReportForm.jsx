// 담당 조원이 완료 보고 메모를 쓰는 form입니다. 기여 항목 줄 안에 열립니다. 입력값과 동작은 page에서 props로 받습니다.
function ContributionReportForm({ memo, onMemoChange, onSubmit, onClose, reporting }) {
    function handleSubmit(event) {
        event.preventDefault()
        onSubmit()
    }

    return (
        <form onSubmit={handleSubmit} className="item-full">
            <label className="field">
                완료 보고 메모(필수, 2000자까지)
                <textarea value={memo} maxLength={2000} rows={3} onChange={(event) => onMemoChange(event.target.value)} />
            </label>
            <p className="muted">보고한 뒤에는 메모를 고칠 수 없습니다.</p>
            <div className="row">
                <button type="submit" disabled={reporting}>보고</button>
                <button type="button" className="secondary" onClick={onClose} disabled={reporting}>닫기</button>
            </div>
        </form>
    )
}

export default ContributionReportForm
