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
                // finally가 아니라 여기서 버튼을 풉니다. 성공하면 다른 화면으로 가서 이 화면이 사라지기 때문입니다.
                setJoining(false)
            })
    }

    return (
        <main className="join-page">
            <section className="card join-box">
                <div>
                    <h1>이 조에 합류할까요?</h1>
                    <p className="sub">합류하면 이 과제에서 다른 조로 옮길 수 없습니다.</p>
                </div>
                {error && <p className="err" role="alert">{error}</p>}
                <div className="row">
                    <button type="button" className="btn primary" onClick={handleJoin} disabled={joining}>합류</button>
                    <Link to="/" className="btn">취소</Link>
                </div>
            </section>
        </main>
    )
}

export default JoinPage
