import RecordRow from './RecordRow'

// 기록 여러 줄을 받은 순서대로 그립니다. 건수는 세지 않습니다. 데이터는 page에서 props로 받습니다.
function RecordList({ teamId, records, emptyText }) {
    if (records.length === 0) {
        return <div className="empty"><p className="sub">{emptyText}</p></div>
    }

    // 줄에는 고유 번호가 없고 시각과 종류가 같은 줄도 있어서 순서 번호를 key에 함께 씁니다. 받은 목록의 순서는 바뀌지 않습니다.
    return records.map((record, index) => (
        <RecordRow key={`${record.type}-${record.occurredAt}-${index}`} teamId={teamId} record={record} />
    ))
}

export default RecordList
