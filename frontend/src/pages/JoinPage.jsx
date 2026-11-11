import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { getJoinPreview, joinTeam } from '../api/teams'

// 초대 링크(/join/:inviteCode)를 연 학생에게 어느 과제의 어느 조인지 먼저 보여 주고, 버튼을 눌러 합류하게 합니다. 성공하면 조 화면으로 갑니다.
// 화면을 열자마자 합류하지 않고 버튼을 누르게 합니다. 잘못 연 링크로 바로 합류되지 않게 하기 위해서입니다.
function JoinPage() {
    const { inviteCode } = useParams()
    // 미리보기 { assignmentTitle, teamName, leaderName }입니다. 서버는 합류와 같은 검사를 해서, 합류할 수 없는 링크면 오류를 줍니다.
    const [preview, setPreview] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    // 합류 버튼의 오류입니다. 미리보기 오류(error)와 나눠서, 실패해도 미리보기는 그대로 둡니다.
    const [joinError, setJoinError] = useState(null)
    const [joining, setJoining] = useState(false)
    const navigate = useNavigate()

    // 화면 안에서 다른 초대 링크로 옮겨 가면 같은 화면이 다시 쓰입니다. 그래서 받은 결과마다 이전 링크의 미리보기·오류를 지웁니다.
    useEffect(() => {
        // 링크가 빠르게 바뀌면 늦게 온 옛 응답이 화면을 덮어쓸 수 있습니다. 링크가 바뀌면 이전 요청의 결과를 버립니다.
        let ignore = false
        getJoinPreview(inviteCode)
            .then((data) => {
                if (ignore) return
                setPreview(data)
                setError(null)
                setJoinError(null)
            })
            .catch((err) => {
                if (ignore) return
                setPreview(null)
                setError(err.message)
            })
            .finally(() => {
                if (!ignore) setLoading(false)
            })
        return () => {
            ignore = true
        }
    }, [inviteCode])

    function handleJoin() {
        setJoinError(null)
        setJoining(true)
        joinTeam(inviteCode)
            .then((team) => navigate(`/teams/${team.id}`))
            .catch((err) => {
                setJoinError(err.message)
                // finally가 아니라 여기서 버튼을 풉니다. 성공하면 다른 화면으로 가서 이 화면이 사라지기 때문입니다.
                setJoining(false)
            })
    }

    if (loading) {
        return (
            <main className="join-page">
                <section className="card join-box" aria-busy="true">
                    <p className="sub">초대 링크를 확인하는 중입니다.</p>
                </section>
            </main>
        )
    }

    if (error) {
        return (
            <main className="join-page">
                <section className="card join-box" role="alert">
                    <h1>이 링크로 합류할 수 없습니다</h1>
                    <p className="err">{error}</p>
                    <div className="row">
                        <Link to="/" className="btn">내 과제로 가기</Link>
                    </div>
                </section>
            </main>
        )
    }

    return (
        <main className="join-page">
            <section className="card join-box">
                <div>
                    <h1>이 조에 합류할까요?</h1>
                    <p className="sub">합류하면 이 과제에서 다른 조로 옮길 수 없습니다.</p>
                </div>
                <dl className="props">
                    <dt>과제</dt>
                    <dd>{preview.assignmentTitle}</dd>
                    <dt>조</dt>
                    <dd>{preview.teamName}</dd>
                    <dt>조장</dt>
                    <dd>{preview.leaderName}</dd>
                </dl>
                {joinError && <p className="err" role="alert">{joinError}</p>}
                <div className="row">
                    <button type="button" className="btn primary" onClick={handleJoin} disabled={joining}>합류</button>
                    <Link to="/" className="btn">취소</Link>
                </div>
            </section>
        </main>
    )
}

export default JoinPage
