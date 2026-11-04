// 되돌리기 어려운 동작을 한 번 더 묻는 상자입니다. 내용과 동작은 page에서 props로 받습니다.
// okClass: 확인 버튼 모양입니다. 평가 시작처럼 주요 동작이면 primary, 삭제처럼 되돌릴 수 없으면 danger입니다.
function ConfirmBox({ title, body, okLabel, okClass, onOk, onCancel, busy }) {
    return (
        // aria-live: 상자가 나타나면 화면 읽기 프로그램이 내용을 읽어 줍니다.
        <section className="confirm" aria-live="polite">
            <h2>{title}</h2>
            <p className="sub">{body}</p>
            <div className="row">
                <button type="button" className={`btn ${okClass}`} onClick={onOk} disabled={busy}>{okLabel}</button>
                <button type="button" className="btn" onClick={onCancel} disabled={busy}>취소</button>
            </div>
        </section>
    )
}

export default ConfirmBox
