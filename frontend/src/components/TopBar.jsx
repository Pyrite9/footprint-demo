// 본문 맨 위의 상자입니다. 지금 위치(crumbs)를 왼쪽에, 그 화면의 주요 버튼(children)을 오른쪽에 놓습니다.
function TopBar({ crumbs, children }) {
    return (
        <header className="topbar">
            <div className="crumbs">
                {crumbs.map((crumb) => <span key={crumb} className="crumb">{crumb}</span>)}
            </div>
            {children}
        </header>
    )
}

export default TopBar
