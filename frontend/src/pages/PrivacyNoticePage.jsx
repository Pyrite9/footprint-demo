import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { consentToPrivacy, getMe } from '../api/auth'
import { removeToken } from '../api/token'
import {
    PRIVACY_NOTICE_CONSENT_GUIDE,
    PRIVACY_NOTICE_DRAFT_BANNER,
    PRIVACY_NOTICE_INTRO,
    PRIVACY_NOTICE_SECTIONS,
    PRIVACY_NOTICE_TITLE,
} from '../constants/privacyNotice'
import { formatDateTime } from '../format'

// 개인정보 고지를 보여 주고 동의를 받습니다(X-2). 동의하면 원래 가려던 주소(없으면 첫 화면)로 갑니다.
// 동의 전에는 RequirePrivacyConsent가 다른 화면에서 이곳으로 보냅니다. 이미 동의한 회원이 열면 고지와 동의 시각만 보여 줍니다.
// 문구는 constants/privacyNotice.js에 있고, 학교·담당 교수 확인 전 초안입니다.
function PrivacyNoticePage() {
    const location = useLocation()
    const from = location.state?.from ?? '/'
    const navigate = useNavigate()
    // 내 정보입니다. privacyConsentedAt으로 동의 버튼을 보일지 정합니다.
    const [me, setMe] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    // 동의 버튼의 오류입니다. 불러오기 오류(error)와 나눠서, 실패해도 고지는 그대로 둡니다.
    const [consentError, setConsentError] = useState(null)
    const [consenting, setConsenting] = useState(false)

    useEffect(() => {
        getMe()
            .then((data) => setMe(data))
            .catch((err) => setError(err.message))
            .finally(() => setLoading(false))
    }, [])

    function handleConsent() {
        setConsentError(null)
        setConsenting(true)
        consentToPrivacy()
            // replace: 동의한 뒤 뒤로 가기를 눌러도 고지 화면으로 돌아오지 않게 합니다.
            .then(() => navigate(from, { replace: true }))
            .catch((err) => {
                setConsentError(err.message)
                // finally가 아니라 여기서 버튼을 풉니다. 성공하면 다른 화면으로 가서 이 화면이 사라지기 때문입니다.
                setConsenting(false)
            })
    }

    // 동의하지 않는 길입니다. token을 지우고 로그인 화면으로 갑니다. 서버에 남는 동의 기록은 없습니다.
    function handleLogout() {
        removeToken()
        navigate('/login')
    }

    if (loading) {
        return (
            <main className="join-page">
                {/* 불러오는 중이라는 표시는 progress 요소로 둡니다. 값(value)이 없으면 끝을 모르는 진행 막대가 됩니다. */}
                <progress aria-label="안내를 불러오는 중" />
            </main>
        )
    }

    if (error) {
        return (
            <main className="join-page">
                <section className="card join-box" role="alert">
                    <h1>안내를 불러오지 못했습니다</h1>
                    <p className="err">{error}</p>
                    <div className="row">
                        <button type="button" className="btn" onClick={handleLogout}>다시 로그인</button>
                    </div>
                </section>
            </main>
        )
    }

    const consented = me.privacyConsentedAt !== null

    return (
        <main className="join-page">
            <article className="card join-box notice-box">
                <h1>{PRIVACY_NOTICE_TITLE}</h1>
                {PRIVACY_NOTICE_DRAFT_BANNER && <p className="lockband">{PRIVACY_NOTICE_DRAFT_BANNER}</p>}
                <p>{PRIVACY_NOTICE_INTRO}</p>
                {PRIVACY_NOTICE_SECTIONS.map((section) => (
                    <section key={section.title} className="notice-section">
                        <h2>{section.title}</h2>
                        <ul>
                            {section.items.map((item) => (
                                <li key={item}>{item}</li>
                            ))}
                        </ul>
                    </section>
                ))}
                {consented ? (
                    <>
                        <p className="sub">{formatDateTime(me.privacyConsentedAt)}에 동의했습니다.</p>
                        <div className="row">
                            <Link to={from} className="btn">돌아가기</Link>
                        </div>
                    </>
                ) : (
                    <>
                        <p className="sub">{PRIVACY_NOTICE_CONSENT_GUIDE}</p>
                        {consentError && <p className="err" role="alert">{consentError}</p>}
                        {/* 동의와 거절(로그아웃)을 같은 크기의 버튼으로 나란히 둡니다. 미리 체크된 칸 같은 장치로 동의를 유도하지 않습니다. */}
                        <div className="row">
                            <button type="button" className="btn primary" onClick={handleConsent} disabled={consenting}>동의합니다</button>
                            <button type="button" className="btn" onClick={handleLogout}>동의하지 않고 로그아웃</button>
                        </div>
                    </>
                )}
            </article>
        </main>
    )
}

export default PrivacyNoticePage
