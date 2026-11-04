// 조의 산출물 목록을 역할별로 묶어, 역할마다 최신순으로 그립니다. 데이터와 동작은 page에서 props로 받습니다.
// 순번: 같은 역할의 산출물에 올린 조원과 관계없이 올린 순서대로 1, 2, 3…을 붙입니다. 삭제된 산출물도 세어서 순번이 끊기지 않습니다. 조회 수·다운로드 수는 없습니다.
// 삭제 버튼은 canDelete(기록 단계)이고 내가 올린 산출물에만 보여 줍니다. 삭제된 산출물은 파일이 없어서 다운로드 버튼도 없습니다.
function DeliverableList({ deliverables, currentMemberId, canDelete, onDownload, onDelete, downloadingId, deleteDisabled }) {
    if (deliverables.length === 0) {
        return <p>아직 올린 산출물이 없습니다.</p>
    }

    // 역할 순서는 가나다순으로 고정합니다. 많이 올린 순이나 최근 순으로 두면 역할끼리 비교하는 순위처럼 보입니다.
    const roleNames = [...new Set(deliverables.map((deliverable) => deliverable.roleName))].sort((a, b) => a.localeCompare(b))

    return roleNames.map((roleName) => (
        <section key={roleName}>
            <h3>{roleName}</h3>
            <ul className="item-list">
                {deliverables.filter((deliverable) => deliverable.roleName === roleName).map((deliverable) => (
                    <li key={deliverable.id}>
                        <span className="badge">{sequenceOf(deliverables, deliverable)}번째</span>
                        {deliverable.deletedAt === null ? <strong>{deliverable.fileName}</strong> : <span className="muted">{deliverable.fileName}</span>}
                        {deliverable.deletedAt !== null && <span className="badge">삭제됨</span>}
                        <span className="muted">
                            {formatFileSize(deliverable.fileSize)} · {deliverable.uploaderName} · {deliverable.uploadedAt.replace('T', ' ')}
                            {deliverable.deletedAt !== null && ` · 삭제 ${deliverable.deletedAt.replace('T', ' ')}`}
                        </span>
                        {deliverable.deletedAt === null && (
                            <div className="row end">
                                <button
                                    type="button"
                                    className="secondary"
                                    onClick={() => onDownload(deliverable)}
                                    disabled={downloadingId === deliverable.id}
                                >
                                    다운로드
                                </button>
                                {canDelete && deliverable.uploaderMemberId === currentMemberId && (
                                    <button type="button" className="secondary" onClick={() => onDelete(deliverable)} disabled={deleteDisabled}>삭제</button>
                                )}
                            </div>
                        )}
                    </li>
                ))}
            </ul>
        </section>
    ))
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
