import TopBar from './TopBar'

// 화면 데이터를 불러오는 동안 보여 줍니다. 내용이 들어올 자리를 회색 막대로 채워 화면이 덜 흔들리게 합니다.
function PageLoading() {
    return (
        <>
            <TopBar crumbs={[]} />
            <main className="content">
                <section className="card pad" aria-busy="true">
                    <p className="sub">불러오는 중입니다.</p>
                    <div className="skel" style={{ width: '62%' }} />
                    <div className="skel" style={{ width: '84%' }} />
                    <div className="skel" style={{ width: '48%' }} />
                </section>
            </main>
        </>
    )
}

export default PageLoading
