// 과제를 만든 교수에게 평가 시작 버튼을 보여 줍니다. 동작은 page에서 props로 받습니다.
function StartEvaluationPanel({ onStart, starting }) {
    return (
        <section className="panel">
            <h2>평가 시작</h2>
            <p>평가를 시작하면 조원이 동료평가를 입력할 수 있습니다. 초대, 역할 지정·변경, 조장 교체, 산출물 올리기·삭제, 기여 항목 등록·보고·취소는 더 할 수 없습니다.</p>
            <p className="muted">시작한 뒤에는 기록 단계로 되돌릴 수 없습니다.</p>
            <button type="button" onClick={onStart} disabled={starting}>평가 시작</button>
        </section>
    )
}

export default StartEvaluationPanel
