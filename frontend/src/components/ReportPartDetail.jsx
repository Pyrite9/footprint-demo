import { formatDateTime } from '../format'

// 오른쪽 상세에 보고서 파트 하나를 그립니다. 취소 버튼은 기록 단계의 조장에게 취소하지 않은 파트에만 보여 줍니다(서버도 확인합니다).
// 데이터와 동작은 page에서 props로 받습니다.
function ReportPartDetail({ part, isRecordingLeader, onCancel, busy }) {
    return (
        <>
            {part.canceledAt !== null && <div><span className="pill gray">취소됨</span></div>}
            <dl className="props">
                <dt>담당</dt>
                <dd>{part.assigneeName}</dd>
                <dt>등록</dt>
                <dd>{formatDateTime(part.createdAt)}</dd>
                {part.canceledAt !== null && <dt>취소</dt>}
                {part.canceledAt !== null && <dd>{formatDateTime(part.canceledAt)}</dd>}
            </dl>
            <p className="sub small">파트의 글은 담당 조원이 씁니다. 글쓰기 화면은 아직 없습니다.</p>
            {isRecordingLeader && part.canceledAt === null && (
                <div><button type="button" className="btn danger" onClick={onCancel} disabled={busy}>파트 취소</button></div>
            )}
        </>
    )
}

export default ReportPartDetail
