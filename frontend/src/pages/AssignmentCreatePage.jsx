import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { createAssignment } from '../api/assignments'

// 교수가 과제명, 기간, 조마다 조장 이메일을 입력해 과제를 만듭니다. 조 개수는 조장 이메일 칸의 개수입니다.
// 교수인지는 서버가 확인합니다. 학생이 이 주소를 열고 제출하면 서버의 403 메시지가 보입니다.
function AssignmentCreatePage() {
    const [title, setTitle] = useState('')
    const [startDate, setStartDate] = useState('')
    const [endDate, setEndDate] = useState('')
    // 조 하나에 칸 하나입니다. 처음에는 조 1개로 시작합니다.
    const [leaderEmails, setLeaderEmails] = useState([''])
    const [error, setError] = useState(null)
    const [submitting, setSubmitting] = useState(false)
    const navigate = useNavigate()

    // index번째 조장 이메일을 바꿉니다. 배열 state는 직접 고치지 않고 새 배열로 바꿔 끼웁니다.
    function handleLeaderEmailChange(index, value) {
        setLeaderEmails(leaderEmails.map((email, i) => (i === index ? value : email)))
    }

    // 조를 하나 늘립니다.
    function handleAddTeam() {
        setLeaderEmails([...leaderEmails, ''])
    }

    // index번째 조를 뺍니다. 조는 최소 1개라서 하나 남으면 버튼을 숨깁니다.
    function handleRemoveTeam(index) {
        setLeaderEmails(leaderEmails.filter((email, i) => i !== index))
    }

    function handleSubmit(event) {
        event.preventDefault()
        setError(null)
        // 응답을 기다리는 동안 버튼을 막아 같은 과제가 두 번 만들어지지 않게 합니다.
        setSubmitting(true)

        createAssignment({ title, startDate, endDate, leaderEmails })
            .then((assignment) => navigate(`/assignments/${assignment.id}`))
            .catch((err) => {
                setError(err.message)
                setSubmitting(false)
            })
    }

    return (
        <main>
            <h1>과제 만들기</h1>
            <p>등록한 과제는 고치거나 지울 수 없습니다.</p>
            <form onSubmit={handleSubmit}>
                <p>
                    <label>
                        과제명{' '}
                        <input value={title} onChange={(event) => setTitle(event.target.value)} />
                    </label>
                </p>
                <p>
                    <label>
                        시작일{' '}
                        <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
                    </label>{' '}
                    <label>
                        종료일{' '}
                        <input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
                    </label>
                </p>

                <h2>조와 조장 ({leaderEmails.length}개 조)</h2>
                {leaderEmails.map((email, index) => (
                    // 칸에는 따로 id가 없어서 순서(index)를 key로 씁니다. 입력값은 state에서 오므로 칸을 빼도 값이 어긋나지 않습니다.
                    <p key={index}>
                        <label>
                            {index + 1}조 조장 이메일{' '}
                            <input
                                type="email"
                                value={email}
                                onChange={(event) => handleLeaderEmailChange(index, event.target.value)}
                            />
                        </label>
                        {leaderEmails.length > 1 && (
                            <button type="button" onClick={() => handleRemoveTeam(index)}>빼기</button>
                        )}
                    </p>
                ))}
                <p>
                    <button type="button" onClick={handleAddTeam}>조 추가</button>
                </p>

                <button type="submit" disabled={submitting}>과제 만들기</button>
            </form>
            {error && <p>{error}</p>}
            <p><Link to="/">돌아가기</Link></p>
        </main>
    )
}

export default AssignmentCreatePage
