import { useState } from 'react'
import { Link, useNavigate, useOutletContext } from 'react-router'
import { createAssignment } from '../api/assignments'
import Icon from '../components/Icon'
import TopBar from '../components/TopBar'

// 교수가 과제명, 기간, 조마다 조장 이메일을 입력해 과제를 만듭니다. 조 개수는 조장 이메일 칸의 개수입니다.
// 등록한 과제는 고칠 수 없어서, 입력 → 확인 → 등록 순서로 한 번 더 보여 줍니다.
// 교수인지는 서버가 확인합니다. 학생이 이 주소를 열고 제출하면 서버의 403 메시지가 보입니다.
function AssignmentCreatePage() {
    const { reloadLayout } = useOutletContext()
    const [title, setTitle] = useState('')
    const [startDate, setStartDate] = useState('')
    const [endDate, setEndDate] = useState('')
    // 조 하나에 칸 하나입니다. 처음에는 조 1개로 시작합니다.
    const [leaderEmails, setLeaderEmails] = useState([''])
    // true이면 입력 화면 대신 확인 화면을 보여 줍니다.
    const [reviewing, setReviewing] = useState(false)
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

    // 입력을 마치고 확인 화면으로 넘어갑니다. 값이 맞는지는 등록할 때 서버가 확인합니다.
    function handleReview(event) {
        event.preventDefault()
        setError(null)
        setReviewing(true)
    }

    function handleSubmit() {
        setError(null)
        // 응답을 기다리는 동안 버튼을 막아 같은 과제가 두 번 만들어지지 않게 합니다.
        setSubmitting(true)

        // 비운 날짜 칸은 ""가 아니라 null로 보냅니다. 서버는 ""를 날짜 형식 오류로 보고 그 칸 하나만 알려 주지만,
        // null이면 "값이 없습니다"로 다른 빈 칸과 함께 모두 알려 줍니다(400 응답의 틀린 칸).
        createAssignment({ title, startDate: startDate || null, endDate: endDate || null, leaderEmails })
            .then((assignment) => {
                // 왼쪽 레일에 새 과제가 나오도록 틀을 다시 불러옵니다.
                reloadLayout()
                navigate(`/assignments/${assignment.id}`)
            })
            .catch((err) => {
                setError(err.message)
                // 틀린 값을 고칠 수 있게 입력 화면으로 돌아갑니다.
                setReviewing(false)
                // finally가 아니라 여기서 버튼을 풉니다. 성공하면 다른 화면으로 가서 이 화면이 사라지기 때문입니다.
                setSubmitting(false)
            })
    }

    return (
        <>
            <TopBar crumbs={['내 과제', '새 과제']} />
            {error && <div className="errband" role="alert">{error}</div>}
            <main className="content">
                <div className="page-head">
                    <div>
                        <h1>새 과제</h1>
                        <p>과제, 조, 조장을 한 번에 저장합니다.</p>
                    </div>
                </div>

                {!reviewing && (
                    <form onSubmit={handleReview} className="card pad narrow">
                        <label className="field">
                            과제명
                            <input className="input" value={title} onChange={(event) => setTitle(event.target.value)} />
                        </label>
                        <div className="grid3">
                            <label className="field">
                                시작일
                                <input className="input" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
                            </label>
                            <label className="field">
                                종료일
                                <input className="input" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
                            </label>
                        </div>
                        <div className="sec">
                            <h3>조와 조장</h3>
                            <p className="sub small">조 이름은 순서대로 붙습니다.</p>
                            {leaderEmails.map((email, index) => (
                                // 칸에는 따로 id가 없어서 순서(index)를 key로 씁니다. 입력값은 state에서 오므로 칸을 빼도 값이 어긋나지 않습니다.
                                <div key={index} className="team-row">
                                    <b>{index + 1}조</b>
                                    <input
                                        className="input"
                                        type="email"
                                        aria-label={`${index + 1}조 조장 이메일`}
                                        placeholder="조장 이메일"
                                        value={email}
                                        onChange={(event) => handleLeaderEmailChange(index, event.target.value)}
                                    />
                                    {leaderEmails.length > 1 && (
                                        <button type="button" className="icon-btn" aria-label={`${index + 1}조 빼기`} onClick={() => handleRemoveTeam(index)}><Icon name="close" /></button>
                                    )}
                                </div>
                            ))}
                            <button type="button" className="add-row" onClick={handleAddTeam}>+ 조 추가</button>
                        </div>
                        <div className="row">
                            <button type="submit" className="btn primary">입력 내용 확인</button>
                            <Link to="/" className="btn">취소</Link>
                        </div>
                    </form>
                )}

                {reviewing && (
                    <section className="confirm narrow">
                        <h2>이 내용으로 등록할까요?</h2>
                        <p className="sub">등록한 과제는 고치거나 지울 수 없습니다.</p>
                        <dl className="props">
                            <dt>과제명</dt>
                            <dd>{title}</dd>
                            <dt>기간</dt>
                            <dd>{startDate} ~ {endDate}</dd>
                            {leaderEmails.map((email, index) => (
                                <div key={index} className="props-row">
                                    <dt>{index + 1}조</dt>
                                    <dd>조장 {email}</dd>
                                </div>
                            ))}
                        </dl>
                        <div className="row">
                            <button type="button" className="btn primary" onClick={handleSubmit} disabled={submitting}>등록</button>
                            <button type="button" className="btn" onClick={() => setReviewing(false)} disabled={submitting}>돌아가서 고치기</button>
                        </div>
                    </section>
                )}
            </main>
        </>
    )
}

export default AssignmentCreatePage
