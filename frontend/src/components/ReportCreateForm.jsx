import { useId } from 'react'

// 조장이 보고서를 만드는 form입니다. 오른쪽 상세 안에 열립니다. 입력값과 만들기 동작은 page에서 props로 받습니다.
function ReportCreateForm({ title, onTitleChange, onSubmit, creating }) {
    // useId: label의 htmlFor와 input의 id를 잇는 이름을 화면마다 겹치지 않게 만듭니다.
    const titleId = useId()

    // required가 비어 있는 칸을 브라우저가 먼저 막으므로, 여기에 오면 칸이 채워져 있습니다.
    function handleSubmit(event) {
        event.preventDefault()
        onSubmit()
    }

    return (
        <form onSubmit={handleSubmit} className="pad-none">
            <div className="field">
                <label htmlFor={titleId}>보고서 제목</label>
                <input id={titleId} className="input" value={title} maxLength={255} required onChange={(event) => onTitleChange(event.target.value)} />
            </div>
            <p className="sub small">만든 보고서의 제목은 고칠 수 없습니다.</p>
            <button type="submit" className="btn primary" disabled={creating}>보고서 만들기</button>
        </form>
    )
}

export default ReportCreateForm
