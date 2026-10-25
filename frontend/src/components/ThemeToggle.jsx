import { useState } from 'react'
import { getCurrentTheme, saveTheme } from '../theme'

// 라이트·다크 테마를 바꾸는 버튼입니다. 고른 테마는 theme.js가 브라우저에 기억합니다.
// 버튼 글자는 "누르면 바뀔 테마"를 보여 줍니다(라이트 화면에서는 "다크").
function ThemeToggle() {
    // 지금 테마입니다. 이 버튼만 쓰는 화면 상태라서 page가 아니라 여기에 둡니다.
    const [theme, setTheme] = useState(getCurrentTheme())

    function handleClick() {
        const next = theme === 'dark' ? 'light' : 'dark'
        saveTheme(next)
        setTheme(next)
    }

    return (
        <button type="button" className="secondary" onClick={handleClick}>
            {theme === 'dark' ? '라이트' : '다크'}
        </button>
    )
}

export default ThemeToggle
