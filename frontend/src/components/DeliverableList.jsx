import { formatFileSize, formatShortDateTime, roleColorClass, sequenceOf } from '../format'

// 조의 산출물 목록을 역할별로 묶어, 역할마다 최신순으로 그립니다. 줄의 파일 이름을 누르면 오른쪽 상세가 열립니다. 데이터와 동작은 page에서 props로 받습니다.
// 순번: 같은 역할의 산출물에 올린 조원과 관계없이 올린 순서대로 1, 2, 3…을 붙입니다. 조회 수·다운로드 수는 없습니다.
// uploadRoleName: 내가 올릴 수 있는 역할입니다. 그 묶음에만 올리기 버튼을 보여 줍니다. 올릴 수 없으면 null입니다.
function DeliverableList({ deliverables, members, uploadRoleName, selectedId, onSelect, onUpload }) {
    // 역할 순서는 가나다순으로 고정합니다. 많이 올린 순이나 최근 순으로 두면 역할끼리 비교하는 순위처럼 보입니다.
    // 지금 맡은 사람이 없어도 그 역할로 올린 파일이 있으면 묶음이 남습니다. 파일에는 올릴 때의 역할명이 저장되어 있기 때문입니다.
    const currentRoleNames = members.filter((member) => member.roleName !== null).map((member) => member.roleName)
    const uploadedRoleNames = deliverables.map((deliverable) => deliverable.roleName)
    const roleNames = [...new Set([...currentRoleNames, ...uploadedRoleNames])].sort((a, b) => a.localeCompare(b))

    return (
        <div className="groups">
            {roleNames.map((roleName) => {
                const rows = deliverables.filter((deliverable) => deliverable.roleName === roleName)
                const people = members.filter((member) => member.roleName === roleName).map((member) => member.name)

                return (
                    <section key={roleName} className={`card group ${roleColorClass(roleName)}`}>
                        <header className="group-h">
                            <div>
                                <h2><span className="dot" />{roleName}</h2>
                                <p>{people.length > 0 ? people.join(', ') : '지금 맡은 조원 없음'}</p>
                            </div>
                            {roleName === uploadRoleName && <button type="button" className="btn" onClick={onUpload}>파일 올리기</button>}
                        </header>
                        {rows.length === 0 && <p className="sub group-empty">이 역할로 올린 파일이 아직 없습니다.</p>}
                        {rows.length > 0 && (
                            <div className="scroll-x">
                                {rows.map((deliverable) => (
                                    <div key={deliverable.id} className={deliverable.id === selectedId ? 'tr body cols-group sel' : 'tr body cols-group'}>
                                        <span className="sub">{sequenceOf(deliverables, deliverable)}번째</span>
                                        <button type="button" className="cell-btn" onClick={() => onSelect(deliverable.id)}>
                                            <span className={deliverable.deletedAt === null ? undefined : 'struck'}>{deliverable.fileName}</span>
                                        </button>
                                        <span>{deliverable.uploaderName}</span>
                                        <span className="sub">{formatShortDateTime(deliverable.uploadedAt)}</span>
                                        <span className="sub">{formatFileSize(deliverable.fileSize)}</span>
                                        <span>
                                            {deliverable.deletedAt !== null && <span className="pill gray">삭제됨</span>}
                                        </span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </section>
                )
            })}
        </div>
    )
}

export default DeliverableList
