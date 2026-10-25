// 파일 하나를 골라 올리는 form입니다. 고른 파일과 올리기 동작은 page에서 props로 받습니다.
// inputKey: page가 이 값을 바꾸면 React가 파일 칸을 새로 만들어 고른 파일 표시가 지워집니다(파일 칸은 value로 비울 수 없습니다).
function DeliverableUploadForm({ roleName, onFileChange, onUpload, uploading, inputKey }) {
    function handleSubmit(event) {
        event.preventDefault()
        onUpload()
    }

    return (
        <form onSubmit={handleSubmit} className="panel">
            <h2>산출물 올리기</h2>
            <p>올리는 산출물의 역할: <span className="badge">{roleName}</span></p>
            <p className="muted">고친 파일도 덮어쓰지 않고 새로 올립니다. 20MB까지 올릴 수 있습니다.</p>
            <div className="row">
                {/* files[0]: 고른 파일 하나입니다. 고르지 않았으면 undefined라 null로 바꿉니다. */}
                <input key={inputKey} type="file" onChange={(event) => onFileChange(event.target.files[0] ?? null)} />
                <button type="submit" disabled={uploading}>올리기</button>
            </div>
        </form>
    )
}

export default DeliverableUploadForm
