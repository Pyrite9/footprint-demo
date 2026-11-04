import { NavLink } from 'react-router'
import Icon from './Icon'

// 메뉴 상자의 가운데 부분입니다. 지금 주소의 과제와 조, 로그인한 사람의 유형에 따라 메뉴를 고릅니다.
function SideNav({ assignment, team, isProfessor }) {
    if (assignment === null) return <HomeNav isProfessor={isProfessor} />
    if (isProfessor) return <ProfessorNav assignment={assignment} currentTeamId={team ? team.id : null} />
    if (team === null) return <HomeNav isProfessor={false} />
    return <StudentNav team={team} />
}

// 과제를 고르기 전(내 과제, 새 과제)의 메뉴입니다.
function HomeNav({ isProfessor }) {
    return (
        <nav className="side-nav" aria-label="메뉴">
            <NavItem to="/" icon="home" label="내 과제" />
            {isProfessor && <NavItem to="/assignments/new" icon="plus" label="새 과제" />}
        </nav>
    )
}

// 학생 메뉴입니다. 학생은 자기 조만 봅니다.
function StudentNav({ team }) {
    return (
        <nav className="side-nav" aria-label="메뉴">
            <NavItem to={`/teams/${team.id}`} icon="home" label={`${team.name} 홈`} />
            <div className="side-label">과정 기록</div>
            <NavItem to={`/teams/${team.id}/deliverables`} icon="file" label="산출물" />
            <NavItem to={`/teams/${team.id}/contributions`} icon="list" label="기여 항목" />
            <div className="side-label">조</div>
            <NavItem to={`/teams/${team.id}/members`} icon="members" label="조원과 역할" />
            <NavItem to={`/teams/${team.id}/evaluation`} icon="evaluation" label="동료평가">
                {team.phase === 'RECORDING' && <span className="nav-lock">평가 단계부터</span>}
            </NavItem>
        </nav>
    )
}

// 교수 메뉴입니다. 과제의 조를 모두 보여 주고, 지금 보는 조 아래에만 세부 메뉴를 펼칩니다.
function ProfessorNav({ assignment, currentTeamId }) {
    return (
        <nav className="side-nav" aria-label="메뉴">
            <NavItem to={`/assignments/${assignment.id}`} icon="home" label="조 현황" />
            <div className="side-label">조</div>
            {assignment.teams.map((team) => (
                <div key={team.id} className="nav-team">
                    <NavItem to={`/teams/${team.id}`} icon="person" label={team.name} />
                    {team.id === currentTeamId && (
                        <div className="nav-sub">
                            <NavItem to={`/teams/${team.id}/deliverables`} label="산출물" />
                            <NavItem to={`/teams/${team.id}/contributions`} label="기여 항목" />
                            <NavItem to={`/teams/${team.id}/members`} label="조원과 조장" />
                        </div>
                    )}
                </div>
            ))}
        </nav>
    )
}

// 메뉴 줄 하나입니다. NavLink는 지금 주소와 같으면 class에 active를 붙입니다. end: 주소가 정확히 같을 때만 active입니다.
function NavItem({ to, icon, label, children }) {
    return (
        <NavLink to={to} end className="nav">
            {icon && <Icon name={icon} />}
            <span>{label}</span>
            {children}
        </NavLink>
    )
}

export default SideNav
