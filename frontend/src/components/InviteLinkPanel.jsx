// 조장에게 초대 링크와 발급 버튼을 보여 줍니다. 링크 값과 발급 동작은 page에서 props로 받습니다.
// inviteCode가 null이면 아직 발급하지 않은 것입니다.
function InviteLinkPanel({ inviteCode, onIssue, issuing }) {
    // 화면 주소(예: http://localhost:5173)에 /join/코드를 붙인 것이 초대 링크입니다. 이 링크를 조원에게 보냅니다.
    const inviteLink = inviteCode ? `${window.location.origin}/join/${inviteCode}` : null

    return (
        <section className="card pad">
            <div className="row between">
                <h2 className="title">초대 링크</h2>
                {/* 이미 발급했으면 다시 발급은 덜 중요한 동작이라 주요 버튼으로 두지 않습니다. */}
                <button type="button" className={inviteLink ? 'btn' : 'btn primary'} onClick={onIssue} disabled={issuing}>
                    {inviteLink ? '다시 발급' : '초대 링크 발급'}
                </button>
            </div>
            {inviteLink && <div className="memo"><code>{inviteLink}</code></div>}
            <p className="sub">{inviteLink ? '다시 발급하면 이전 링크로는 합류할 수 없습니다.' : '아직 발급하지 않았습니다. 링크를 받은 학생이 이 조에 합류합니다.'}</p>
        </section>
    )
}

export default InviteLinkPanel
