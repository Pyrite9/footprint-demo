import { request } from './client'

// 이메일과 비밀번호로 로그인합니다. 성공하면 { accessToken }을 돌려줍니다.
export function login(email, password) {
  return request('/auth/login', { method: 'POST', body: { email, password } })
}

// 로그인한 회원 자신의 정보 { id, email, name, memberType }를 가져옵니다.
export function getMe() {
  return request('/members/me')
}
