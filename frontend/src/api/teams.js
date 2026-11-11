import { request } from './client'

// 조 정보 { id, name, assignmentId, assignmentTitle, phase, startDate, endDate }를 가져옵니다.
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

// 합류하기 전에 초대 코드의 { assignmentTitle, teamName, leaderName }을 가져옵니다. 합류는 하지 않습니다.
// 주소 안에 넣는 값이라 encodeURIComponent로 /, ? 같은 글자를 주소용 글자로 바꿉니다.
export function getJoinPreview(inviteCode) {
  return request(`/teams/join/${encodeURIComponent(inviteCode)}`)
}

// 초대 코드로 조에 합류하고 합류한 조 { id, name, assignmentId, assignmentTitle, phase, startDate, endDate }를 돌려줍니다.
export function joinTeam(inviteCode) {
  return request('/teams/join', { method: 'POST', body: { inviteCode } })
}

// 조장이 조원(teamMemberId: 조 참여 번호)에게 역할을 지정합니다. 역할이 지정된 조원 { id, memberId, name, roleName, leader }를 돌려줍니다.
export function assignRole(teamId, teamMemberId, roleName) {
  return request(`/teams/${teamId}/members/${teamMemberId}/role`, { method: 'POST', body: { roleName } })
}

// 조장이 조원의 역할을 바꿉니다. 역할이 바뀐 조원 { id, memberId, name, roleName, leader }를 돌려줍니다.
export function changeRole(teamId, teamMemberId, roleName) {
  return request(`/teams/${teamId}/members/${teamMemberId}/role`, { method: 'PUT', body: { roleName } })
}

// 과제를 만든 교수가 조원(teamMemberId: 조 참여 번호)을 새 조장으로 바꿉니다. 새 조장 { id, memberId, name, roleName, leader }를 돌려줍니다.
export function changeLeader(teamId, teamMemberId) {
  return request(`/teams/${teamId}/leader`, { method: 'PUT', body: { teamMemberId } })
}

// 한 조의 조장 교체 이력 [{ id, previousLeaderName, newLeaderName, changedAt }]을 최근에 바뀐 것부터 가져옵니다.
export function getLeaderHistories(teamId) {
  return request(`/teams/${teamId}/leader-histories`)
}

// 한 조원의 역할 이력 [{ id, previousRoleName, changedAt }]을 최근에 바뀐 것부터 가져옵니다.
export function getRoleHistories(teamId, teamMemberId) {
  return request(`/teams/${teamId}/members/${teamMemberId}/role-histories`)
}
