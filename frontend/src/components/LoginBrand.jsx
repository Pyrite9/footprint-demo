import LogoMark from './LogoMark'

// 발자국 무늬에서 색을 칠할 칸과 그 색입니다. 칸 번호는 0부터 41까지입니다. 장식이라 값에 뜻은 없습니다.
const TRAIL_MARKS = { 3: '#7FA2FF', 8: '#4FC9C4', 12: '#F2A25C', 17: '#7FA2FF', 23: '#BF9BFF', 26: '#4FC9C4', 31: '#F2A25C', 36: '#7FA2FF', 40: '#4FC9C4' }
const TRAIL_CELLS = Array.from({ length: 42 }, (unused, index) => index)

// 로그인 화면 왼쪽의 서비스 소개입니다.
function LoginBrand() {
    return (
        <div className="login-brand">
            <div className="login-logo">
                <LogoMark />
                <span>발자국</span>
            </div>
            <div className="login-copy">
                <h1>판단은 사람이,<br />시스템은 근거만.</h1>
                <p>조별과제에서 누가 언제 무엇을 했는지 있는 그대로 남깁니다. 점수를 계산하지 않고, 기록만 보여 줍니다.</p>
            </div>
            <div className="trail" aria-hidden="true">
                {TRAIL_CELLS.map((cell) => (
                    <span key={cell} className={TRAIL_MARKS[cell] ? 'mark' : undefined} style={{ '--trail-color': TRAIL_MARKS[cell] }} />
                ))}
            </div>
        </div>
    )
}

export default LoginBrand
