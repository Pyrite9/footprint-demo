import { request } from './client'

// 한 조원의 근거 기록 { deliverables, contributions, receivedConfirmations, givenConfirmations }를 가져옵니다. 건수는 오지 않습니다.
export function getMemberEvidence(teamId, teamMemberId) {
  return request(`/teams/${teamId}/members/${teamMemberId}/evidence`)
}
