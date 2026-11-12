import { refreshAccessToken, request } from './client'

// 이메일과 비밀번호로 로그인합니다. 성공하면 { accessToken }을 돌려줍니다.
export function login(email, password) {
  return request('/auth/login', { method: 'POST', body: { email, password } })
}

// 구글 버튼이 받은 ID token으로 로그인합니다. 성공하면 { accessToken }을 돌려줍니다. 처음 온 구글 계정은 회원으로 저장됩니다.
export function loginWithGoogle(credential) {
  return request('/auth/google', { method: 'POST', body: { credential } })
}

// 로그인한 회원 자신의 정보 { id, email, name, memberType, privacyConsentedAt }를 가져옵니다.
// privacyConsentedAt은 개인정보 고지에 동의한 시각이고, 동의 전에는 null입니다.
export function getMe() {
  return request('/members/me')
}

// 로그인한 회원 본인이 개인정보 고지에 동의하고, 동의 시각이 담긴 내 정보를 돌려받습니다.
// 시각은 서버가 정하므로 body를 보내지 않습니다. 이미 동의했으면 처음 동의한 시각 그대로 돌아옵니다.
export function consentToPrivacy() {
  return request('/members/me/privacy-consent', { method: 'POST' })
}

// refresh token(cookie)으로 새 access token을 받아 저장합니다. 화면을 새로 열었을 때(새로고침) RequireLogin이 부릅니다.
// 받지 못하면(로그인한 적 없음, 만료, 로그아웃) 오류를 던집니다. 여러 곳에서 동시에 불러도 요청은 하나만 나갑니다.
export function refresh() {
  return refreshAccessToken()
}

// 서버의 refresh token을 지우고 cookie도 지웁니다(204). 화면의 access token은 부른 쪽이 지웁니다.
export function logout() {
  return request('/auth/logout', { method: 'POST' })
}
