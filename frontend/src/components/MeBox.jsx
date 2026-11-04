import { MEMBER_TYPE_LABELS } from '../constants/memberTypes'
import { initialOf } from '../format'
import Icon from './Icon'
import ThemeToggle from './ThemeToggle'

// 메뉴 상자의 아래쪽입니다. 로그인한 사람과 테마 버튼, 로그아웃 버튼을 보여 줍니다.
// me가 null이면 아직 불러오는 중이거나 실패한 것입니다. 그래도 테마와 로그아웃은 쓸 수 있게 둡니다. 실패 이유는 본문 화면이 다시 알려 줍니다.
function MeBox({ me, theme, onToggleTheme, onLogout }) {
    return (
        <div className="me">
            {me && <span className="av">{initialOf(me.name)}</span>}
            <div className="me-text">
                {me && <b>{me.name}</b>}
                {me && <span>{MEMBER_TYPE_LABELS[me.memberType]}</span>}
            </div>
            <ThemeToggle theme={theme} onToggle={onToggleTheme} />
            <button type="button" className="icon-btn" aria-label="로그아웃" title="로그아웃" onClick={onLogout}><Icon name="logout" /></button>
        </div>
    )
}

export default MeBox
