import { useEffect, useEffectEvent, useRef } from 'react'
import { loadGoogleScript } from '../google'

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID

// 구글 로그인 버튼을 그립니다. 사용자가 구글 계정을 고르면 onCredential(ID token)을, 버튼을 못 그리면 onError(메시지)를 부릅니다.
function GoogleLoginButton({ onCredential, onError }) {
    // 구글 script가 버튼을 그려 넣을 칸입니다. useRef는 React가 그린 실제 DOM 요소를 잡아 둡니다.
    const buttonRef = useRef(null)
    // 구글에 넘기는 callback은 처음 한 번만 등록합니다.
    // useEffectEvent로 감싸면 effect를 다시 실행하지 않아도 늘 최신 props(onCredential, onError)를 부릅니다.
    const handleCredential = useEffectEvent((response) => onCredential(response.credential))
    const handleError = useEffectEvent((message) => onError(message))

    useEffect(() => {
        let ignore = false
        loadGoogleScript()
            .then(() => {
                // 그사이 화면을 떠났으면(StrictMode의 두 번 실행 포함) 버튼을 그리지 않습니다. 두 번 등록하면 구글이 경고를 냅니다.
                if (ignore) return
                window.google.accounts.id.initialize({
                    client_id: GOOGLE_CLIENT_ID,
                    callback: (response) => handleCredential(response),
                })
                window.google.accounts.id.renderButton(buttonRef.current, {
                    theme: 'outline',
                    size: 'large',
                    text: 'signin_with',
                    width: 300,
                    locale: 'ko',
                })
            })
            .catch((err) => handleError(err.message))
        return () => {
            ignore = true
        }
    }, [])

    return <div ref={buttonRef} className="google-button" />
}

export default GoogleLoginButton
