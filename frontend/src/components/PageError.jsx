import { Link } from 'react-router'
import TopBar from './TopBar'

// 화면 데이터를 불러오지 못했을 때 보여 줍니다. message는 서버가 준 이유입니다(예: 다른 조의 기록을 연 403).
function PageError({ message }) {
    return (
        <>
            <TopBar crumbs={[]} />
            <main className="content">
                <section className="card empty" role="alert">
                    <b>불러오지 못했습니다</b>
                    <p className="sub">{message}</p>
                    <Link to="/" className="btn">내 과제로 가기</Link>
                </section>
            </main>
        </>
    )
}

export default PageError
