import Icon from './Icon'

// 라이트·다크 테마를 바꾸는 버튼입니다. 지금 테마와 누를 때 할 일은 page에서 props로 받습니다.
// 아이콘과 설명은 "누르면 바뀔 테마"를 보여 줍니다(라이트 화면에서는 달).
function ThemeToggle({ theme, onToggle }) {
    const label = theme === 'dark' ? '라이트 모드로' : '다크 모드로'

    return (
        <button type="button" className="icon-btn" aria-label={label} title={label} onClick={onToggle}>
            <Icon name={theme === 'dark' ? 'sun' : 'moon'} />
        </button>
    )
}

export default ThemeToggle
