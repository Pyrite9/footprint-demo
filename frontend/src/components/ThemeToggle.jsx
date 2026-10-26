// 라이트·다크 테마를 바꾸는 버튼입니다. 지금 테마와 누를 때 할 일은 page에서 props로 받습니다.
// 버튼 글자는 "누르면 바뀔 테마"를 보여 줍니다(라이트 화면에서는 "다크").
function ThemeToggle({ theme, onToggle }) {
    return (
        <button type="button" className="secondary" onClick={onToggle}>
            {theme === 'dark' ? '라이트' : '다크'}
        </button>
    )
}

export default ThemeToggle
