import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { getMe } from '../api/auth'
import { getDeliverables } from '../api/deliverables'
import { getTeam, getTeamMembers } from '../api/teams'
import EvaluationNotice from '../components/EvaluationNotice'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import ProgressTimeline from '../components/ProgressTimeline'
import TopBar from '../components/TopBar'

// 조장과 조원이 보는 진척 화면입니다. 과제 기간 위에 역할별 업로드 시점을 보여 줍니다. 새 API 없이 산출물 목록을 그대로 그립니다.
// 시스템은 진척률을 계산하지 않습니다. 이 일정이 빠른지 늦은지는 사람이 판단합니다.
function ProgressPage() {
    const { teamId } = useParams()
    const [me, setMe] = useState(null)
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [deliverables, setDeliverables] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)

    useEffect(() => {
        Promise.all([getMe(), getTeam(teamId), getTeamMembers(teamId), getDeliverables(teamId)])
            .then(([meData, teamData, membersData, deliverablesData]) => {
                setMe(meData)
                setTeam(teamData)
                setMembers(membersData)
                setDeliverables(deliverablesData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />
    // 진척 화면은 조원용입니다. 교수의 메뉴에는 없고, 주소를 직접 입력해도 그리지 않습니다. 교수가 볼 수 있는 산출물 목록을 그리는 화면이라 서버는 막지 않습니다.
    if (!members.some((member) => member.memberId === me.id)) return <PageError message="진척 화면은 조원만 볼 수 있습니다" />

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, '진척']} />
            <div className="main-row">
                <main className="content">
                    <div className="page-head">
                        <div>
                            <h1>진척</h1>
                            <p>과제 기간 위에 역할별로 파일을 올린 시점을 보여 줍니다. 진척률은 계산하지 않습니다.</p>
                        </div>
                    </div>
                    <EvaluationNotice />
                    <ProgressTimeline team={team} members={members} deliverables={deliverables} />
                </main>
            </div>
        </>
    )
}

export default ProgressPage
