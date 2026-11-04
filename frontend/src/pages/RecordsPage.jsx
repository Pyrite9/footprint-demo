import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { getTeamRecords } from '../api/records'
import { getTeam, getTeamMembers } from '../api/teams'
import PageError from '../components/PageError'
import PageLoading from '../components/PageLoading'
import RecordList from '../components/RecordList'
import TopBar from '../components/TopBar'

// 보기를 고르는 탭입니다.
const VIEW_TABS = ['시간순', '조원별']

// 조의 기록 열람 화면입니다. 조원과 교수가 기록 단계와 평가 단계 모두 봅니다(서버가 확인합니다). 쓰기 기능은 없습니다.
function RecordsPage() {
    const { teamId } = useParams()
    const [team, setTeam] = useState(null)
    const [members, setMembers] = useState([])
    const [records, setRecords] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [viewTab, setViewTab] = useState('시간순')

    useEffect(() => {
        Promise.all([getTeam(teamId), getTeamMembers(teamId), getTeamRecords(teamId)])
            .then(([teamData, membersData, recordsData]) => {
                setTeam(teamData)
                setMembers(membersData)
                setRecords(recordsData)
            })
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [teamId])

    if (loading) return <PageLoading />
    if (error) return <PageError message={error} />

    return (
        <>
            <TopBar crumbs={[team.assignmentTitle, team.name, '전체 기록']} />
            <main className="content">
                <div className="page-head">
                    <div>
                        <h1>전체 기록</h1>
                        <p>이 조에 쌓인 기록을 있는 그대로 보여 줍니다. 동료평가는 넣지 않습니다.</p>
                    </div>
                    <div className="tabs">
                        {VIEW_TABS.map((tab) => (
                            <button key={tab} type="button" className={viewTab === tab ? 'tab on' : 'tab'} onClick={() => setViewTab(tab)}>{tab}</button>
                        ))}
                    </div>
                </div>
                {viewTab === '시간순' && (
                    <section className="card">
                        <RecordList teamId={teamId} records={records} emptyText="아직 쌓인 기록이 없습니다." />
                    </section>
                )}
                {viewTab === '조원별' && members.map((member) => (
                    <section key={member.id} className="card group">
                        <header className="group-h">
                            <h2>{member.name}</h2>
                            {member.roleName !== null && <span className="sub">{member.roleName}</span>}
                        </header>
                        {/* 이 조원이 한 일과, 이 조원의 기록에 남은 확인이나 이 조원에게서 넘어간 조장 교체를 함께 보여 줍니다. */}
                        <RecordList
                            teamId={teamId}
                            records={records.filter((record) => record.teamMemberId === member.id || record.targetTeamMemberId === member.id)}
                            emptyText="이 조원의 기록이 없습니다."
                        />
                    </section>
                ))}
            </main>
        </>
    )
}

export default RecordsPage
