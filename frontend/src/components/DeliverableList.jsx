// 조의 산출물 목록을 최신순으로 그립니다. 데이터와 다운로드 동작은 page에서 props로 받습니다.
// 순번: 같은 역할의 산출물에 올린 조원과 관계없이 올린 순서대로 1, 2, 3…을 붙입니다. 조회 수·다운로드 수는 없습니다.
function DeliverableList({ deliverables, onDownload, downloadingId }) {
    if (deliverables.length === 0) {
        return <p>아직 올린 산출물이 없습니다.</p>
    }

    return (
        <ul>
            {deliverables.map((deliverable) => (
                <li key={deliverable.id}>
                    [{deliverable.roleName} {sequenceOf(deliverables, deliverable)}번째]{' '}
                    {deliverable.fileName} ({formatFileSize(deliverable.fileSize)}) · {deliverable.uploaderName} ·{' '}
                    {deliverable.uploadedAt.replace('T', ' ')}{' '}
                    <button type="button" onClick={() => onDownload(deliverable)} disabled={downloadingId === deliverable.id}>
                        다운로드
                    </button>
                </li>
            ))}
        </ul>
    )
}

// 같은 역할의 산출물 중 이 산출물보다 먼저(또는 같이) 올린 것의 개수가 순번입니다. id가 작을수록 먼저 올린 것입니다.
// 목록에서 계산할 수 있는 값이라 서버에도 state에도 두지 않습니다.
function sequenceOf(deliverables, target) {
    return deliverables.filter((deliverable) => deliverable.roleName === target.roleName && deliverable.id <= target.id).length
}

// byte 수를 읽기 쉬운 단위로 바꿉니다. 예: 52344 → "51.1 KB"
function formatFileSize(bytes) {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export default DeliverableList
