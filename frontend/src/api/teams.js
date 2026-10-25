import { request } from './client'

// 조 정보 { id, name, assignmentId, assignmentTitle }를 가져옵니다.
export function getTeam(teamId) {
  return request(`/teams/${teamId}`)
}

// 조원 목록 [{ id, memberId, name, roleName, leader }]을 가져옵니다. id는 조 참여 번호입니다.
export function getTeamMembers(teamId) {
  return request(`/teams/${teamId}/members`)
}

// 지금 유효한 초대 코드 { inviteCode }를 가져옵니다. 조장만 부를 수 있습니다.
export function getInviteCode(teamId) {
  return request(`/teams/${teamId}/invite-code`)
}

// 초대 코드를 새로 발급하고 { inviteCode }를 돌려줍니다. 이전 코드는 무효가 됩니다.
export function issueInviteCode(teamId) {
  return request(`/teams/${teamId}/invite-code`, { method: 'POST' })
}

// 초대 코드로 조에 합류하고 합류한 조 { id, name, assignmentId, assignmentTitle }를 돌려줍니다.
export function joinTeam(inviteCode) {
  return request('/teams/join', { method: 'POST', body: { inviteCode } })
}

// 조장이 조원(teamMemberId: 조 참여 번호)에게 역할을 지정합니다. 역할이 지정된 조원 { id, memberId, name, roleName, leader }를 돌려줍니다.
export function assignRole(teamId, teamMemberId, roleName) {
  return request(`/teams/${teamId}/members/${teamMemberId}/role`, { method: 'POST', body: { roleName } })
}
