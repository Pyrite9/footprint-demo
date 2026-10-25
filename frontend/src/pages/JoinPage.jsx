import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { joinTeam } from '../api/teams'

// 초대 링크(/join/:inviteCode)를 연 학생이 버튼을 눌러 조에 합류합니다. 성공하면 조 화면으로 갑니다.
// 화면을 열자마자 합류하지 않고 버튼을 누르게 합니다. 잘못 연 링크로 바로 합류되지 않게 하기 위해서입니다.
function JoinPage() {
    const { inviteCode } = useParams()
    const [error, setError] = useState(null)
    const [joining, setJoining] = useState(false)
    const navigate = useNavigate()

    function handleJoin() {
        setError(null)
        setJoining(true)
        joinTeam(inviteCode)
            .then((team) => navigate(`/teams/${team.id}`))
            .catch((err) => {
                setError(err.message)
                setJoining(false)
            })
    }

    return (
        <main>
            <p className="links"><Link to="/">← 과제 목록</Link></p>
            <h1>조 합류</h1>
            <div className="panel">
                <p>초대 링크로 조에 합류합니다. 한 과제에서는 한 조에만 합류할 수 있습니다.</p>
                <button type="button" onClick={handleJoin} disabled={joining}>합류하기</button>
            </div>
            {error && <p className="error">{error}</p>}
        </main>
    )
}

export default JoinPage
