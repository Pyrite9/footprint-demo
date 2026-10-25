// 조장에게 초대 링크와 발급 버튼을 보여 줍니다. 링크 값과 발급 동작은 page에서 props로 받습니다.
// inviteCode가 null이면 아직 발급하지 않은 것입니다.
function InviteLinkPanel({ inviteCode, onIssue, issuing }) {
    // 화면 주소(예: http://localhost:5173)에 /join/코드를 붙인 것이 초대 링크입니다. 이 링크를 조원에게 보냅니다.
    const inviteLink = inviteCode ? `${window.location.origin}/join/${inviteCode}` : null

    return (
        <section className="panel">
            <h2>초대 링크</h2>
            {inviteLink ? <p><code className="invite-link">{inviteLink}</code></p> : <p>아직 발급하지 않았습니다.</p>}
            <p className="muted">다시 발급하면 이전 링크로는 합류할 수 없습니다.</p>
            {/* 이미 발급했으면 다시 발급은 덜 중요한 동작이라 흰 버튼(secondary)으로 둡니다. */}
            <button type="button" className={inviteLink ? 'secondary' : undefined} onClick={onIssue} disabled={issuing}>
                {inviteLink ? '다시 발급' : '발급'}
            </button>
        </section>
    )
}

export default InviteLinkPanel
