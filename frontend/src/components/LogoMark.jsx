// 발자국 로고입니다. 레일과 로그인 화면에 씁니다. 색은 글자색을 따라갑니다(index.css의 .logo).
function LogoMark() {
    return (
        <svg className="logo" viewBox="0 0 24 24" aria-hidden="true">
            <ellipse cx="8.2" cy="8.6" rx="3.3" ry="5.1" transform="rotate(-14 8.2 8.6)" />
            <ellipse cx="15.8" cy="15.4" rx="3.3" ry="5.1" transform="rotate(14 15.8 15.4)" />
        </svg>
    )
}

export default LogoMark
