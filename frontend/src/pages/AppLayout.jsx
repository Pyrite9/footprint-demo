import { useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate, useParams } from 'react-router'
import { getAssignment, getAssignments } from '../api/assignments'
import { getMe } from '../api/auth'
import { getTeam } from '../api/teams'
import { removeToken } from '../api/token'
import AssignmentRail from '../components/AssignmentRail'
import MeBox from '../components/MeBox'
import SideHead from '../components/SideHead'
import SideNav from '../components/SideNav'
import { getCurrentTheme, saveTheme } from '../theme'

// 로그인한 뒤 대부분의 화면을 감싸는 공통 틀입니다. 왼쪽에 과제 레일과 메뉴를 그리고, 오른쪽 <Outlet /> 자리에 각 화면을 그립니다.
// App.jsx에서 화면들의 바깥 Route로 둡니다. 메뉴에 쓸 데이터를 불러오고 테마 state를 가지므로 pages에 둡니다(데이터와 state는 page가 갖습니다).
function AppLayout() {
    // 바깥 Route에서도 안쪽 주소의 :teamId, :assignmentId를 읽을 수 있습니다. 메뉴는 이 값으로 지금 어느 과제인지 압니다.
    const { teamId, assignmentId } = useParams()
    const location = useLocation()
    const [me, setMe] = useState(null)
    const [assignments, setAssignments] = useState([])
    // 지금 주소의 조와 과제입니다. 내 과제 화면처럼 주소에 없으면 둘 다 null입니다.
    const [current, setCurrent] = useState({ team: null, assignment: null })
    // 안쪽 화면이 과제를 만들거나 평가를 시작하면 1 늘려서 레일과 메뉴를 다시 불러옵니다.
    const [reloadCount, setReloadCount] = useState(0)
    // 지금 테마('light' 또는 'dark')입니다. 고른 테마는 theme.js가 브라우저에 기억합니다.
    const [theme, setTheme] = useState(getCurrentTheme())
    const navigate = useNavigate()

    // 틀은 화면을 옮겨도 다시 그려지지 않아서, 내 정보는 로그인 뒤 한 번만 불러옵니다. 실패 이유는 안쪽 화면이 다시 알려 줍니다.
    useEffect(() => {
        getMe()
            .then((data) => setMe(data))
            .catch(() => setMe(null))
    }, [])

    useEffect(() => {
        getAssignments()
            .then((data) => setAssignments(data))
            .catch(() => setAssignments([]))
    }, [reloadCount])

    useEffect(() => {
        // 조를 빠르게 옮겨 다니면 늦게 온 옛 응답이 메뉴를 덮어쓸 수 있습니다. 주소가 바뀌면 이전 요청의 결과를 버립니다.
        let ignore = false
        loadCurrent(teamId, assignmentId)
            .then((data) => {
                if (!ignore) setCurrent(data)
            })
            // 볼 수 없는 조(403)나 없는 조(404)이면 메뉴만 비웁니다. 이유는 안쪽 화면이 알려 줍니다.
            .catch(() => {
                if (!ignore) setCurrent({ team: null, assignment: null })
            })
        return () => {
            ignore = true
        }
    }, [teamId, assignmentId, reloadCount])

    // 테마를 반대로 바꾸고 기억합니다.
    function handleThemeToggle() {
        const next = theme === 'dark' ? 'light' : 'dark'
        saveTheme(next)
        setTheme(next)
    }

    // token을 지우고 로그인 화면으로 갑니다. 서버는 token을 기억하지 않으므로 서버에 알릴 것이 없습니다.
    function handleLogout() {
        removeToken()
        navigate('/login')
    }

    const isProfessor = me !== null && me.memberType === 'PROFESSOR'

    return (
        <div className="app">
            <AssignmentRail
                assignments={assignments}
                currentAssignmentId={current.assignment ? current.assignment.id : null}
                isProfessor={isProfessor}
            />
            <div className="side-col">
                <div className="side-in">
                    {current.assignment && <SideHead assignment={current.assignment} />}
                    <SideNav assignment={current.assignment} team={current.team} isProfessor={isProfessor} />
                    <MeBox me={me} theme={theme} onToggleTheme={handleThemeToggle} onLogout={handleLogout} />
                </div>
            </div>
            <div className="main">
                {/* key: 주소가 바뀌면 안쪽 화면을 새로 만듭니다. 1조에서 2조로 옮길 때 1조의 입력값과 열어 둔 상세가 남지 않게 합니다. */}
                {/* context: 안쪽 화면이 useOutletContext()로 꺼내 씁니다. */}
                <Outlet key={location.pathname} context={{ reloadLayout: () => setReloadCount(reloadCount + 1) }} />
            </div>
        </div>
    )
}

// 주소의 조나 과제로 메뉴에 쓸 데이터를 불러옵니다. 조 화면이면 그 조의 과제까지 이어서 불러옵니다.
function loadCurrent(teamId, assignmentId) {
    if (teamId) {
        return getTeam(teamId).then((team) => getAssignment(team.assignmentId).then((assignment) => ({ team, assignment })))
    }
    if (assignmentId) {
        return getAssignment(assignmentId).then((assignment) => ({ team: null, assignment }))
    }
    return Promise.resolve({ team: null, assignment: null })
}

export default AppLayout
