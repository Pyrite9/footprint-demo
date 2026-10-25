import { request } from './client'

// 내가 이 조에서 동료평가를 제출했는지 { submitted, submittedAt }를 가져옵니다. 점수는 오지 않습니다.
export function getMyEvaluationStatus(teamId) {
  return request(`/teams/${teamId}/evaluations/mine`)
}

// 자신을 뺀 조원 전원의 평가 [{ targetTeamMemberId, goalSharing, ..., comment }]를 한 번에 제출하고 제출 여부를 돌려줍니다.
export function submitEvaluations(teamId, evaluations) {
  return request(`/teams/${teamId}/evaluations`, { method: 'POST', body: { evaluations } })
}
