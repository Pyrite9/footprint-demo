import { Link } from 'react-router'

// 과제의 조 목록을 상자로 그립니다. 조를 누르면 그 조 화면으로 갑니다. 데이터는 page에서 props로 받습니다.
function TeamSummaryList({ teams }) {
    if (teams.length === 0) {
        return <section className="card empty"><b>볼 수 있는 조가 없습니다</b></section>
    }

    return (
        <div className="grid3">
            {teams.map((team) => (
                <section key={team.id} className="card pad">
                    <div className="row between">
                        <h2 className="title">{team.name}</h2>
                        <Link to={`/teams/${team.id}`} className="btn">기록 보기</Link>
                    </div>
                    <p className="sub">조장 {team.leaderName}</p>
                </section>
            ))}
        </div>
    )
}

export default TeamSummaryList
