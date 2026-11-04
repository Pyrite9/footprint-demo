import { roleColorClass } from '../format'
import Icon from './Icon'

// 파일 하나를 골라 올리는 form입니다. 오른쪽 상세 안에 열립니다. 고른 파일과 올리기 동작은 page에서 props로 받습니다.
function DeliverableUploadForm({ roleName, selectedFile, onFileChange, onUpload, uploading }) {
    function handleSubmit(event) {
        event.preventDefault()
        onUpload()
    }

    return (
        <form onSubmit={handleSubmit} className="pad-none">
            <dl className="props">
                <dt>역할</dt>
                <dd className={`role ${roleColorClass(roleName)}`}><span className="dot" />{roleName}</dd>
            </dl>
            <label className="drop">
                <Icon name="upload" />
                <b>{selectedFile ? selectedFile.name : '파일 고르기'}</b>
                <span className="sub small">20MB까지 올릴 수 있습니다.</span>
                {/* files[0]: 고른 파일 하나입니다. 고르지 않았으면 undefined라 null로 바꿉니다. */}
                <input type="file" onChange={(event) => onFileChange(event.target.files[0] ?? null)} />
            </label>
            <p className="sub small">고친 파일도 덮어쓰지 않고 새로 올립니다. 올릴 때의 역할 이름이 그대로 남습니다.</p>
            <button type="submit" className="btn primary" disabled={uploading || selectedFile === null}>올리기</button>
        </form>
    )
}

export default DeliverableUploadForm
