import { Link } from 'react-router'

// 과제 상세의 조 목록을 그립니다. 조를 누르면 조 화면으로 갑니다. 데이터는 page에서 props로 받습니다.
function TeamSummaryList({ teams }) {
    return (
        <ul className="item-list">
            {teams.map((team) => (
                <li key={team.id}>
                    <Link to={`/teams/${team.id}`}>{team.name}</Link>
                    <span className="muted">조장 {team.leaderName}</span>
                </li>
            ))}
        </ul>
    )
}

export default TeamSummaryList
