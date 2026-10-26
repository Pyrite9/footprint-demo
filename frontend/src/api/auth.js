import { request } from './client'

// 이메일과 비밀번호로 로그인합니다. 성공하면 { accessToken }을 돌려줍니다.
export function login(email, password) {
  return request('/auth/login', { method: 'POST', body: { email, password } })
}

// 구글 버튼이 받은 ID token으로 로그인합니다. 성공하면 { accessToken }을 돌려줍니다. 처음 온 구글 계정은 회원으로 저장됩니다.
export function loginWithGoogle(credential) {
  return request('/auth/google', { method: 'POST', body: { credential } })
}

// 로그인한 회원 자신의 정보 { id, email, name, memberType }를 가져옵니다.
export function getMe() {
  return request('/members/me')
}
