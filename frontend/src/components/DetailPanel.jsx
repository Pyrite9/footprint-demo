import Icon from './Icon'

// 오른쪽 상세의 틀입니다. 제목과 닫기 버튼을 그리고, 내용은 children으로 받습니다.
function DetailPanel({ title, onClose, children }) {
    return (
        <aside className="detail" aria-label="상세">
            <div className="detail-in">
                <div className="detail-head">
                    <h2>{title}</h2>
                    <button type="button" className="icon-btn" aria-label="닫기" onClick={onClose}><Icon name="close" /></button>
                </div>
                {children}
            </div>
        </aside>
    )
}

export default DetailPanel
