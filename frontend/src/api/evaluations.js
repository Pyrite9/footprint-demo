import { request } from './client'

// 내가 이 조에서 동료평가를 제출했는지 { submitted, submittedAt }를 가져옵니다. 점수는 오지 않습니다.
export function getMyEvaluationStatus(teamId) {
  return request(`/teams/${teamId}/evaluations/mine`)
}

// 내가 임시 저장한 내용 [{ targetTeamMemberId, goalSharing, ..., comment, savedAt }]을 가져옵니다. 고르지 않은 점수는 null입니다.
export function getMyEvaluationDrafts(teamId) {
  return request(`/teams/${teamId}/evaluations/draft`)
}

// 지금까지 입력한 내용을 임시 저장하고 저장한 내용을 돌려줍니다. 이전 임시 저장은 이 내용으로 바뀝니다.
export function saveEvaluationDrafts(teamId, evaluations) {
  return request(`/teams/${teamId}/evaluations/draft`, { method: 'PUT', body: { evaluations } })
}

// 자신을 뺀 조원 전원의 평가 [{ targetTeamMemberId, goalSharing, ..., comment }]를 한 번에 제출하고 제출 여부를 돌려줍니다.
export function submitEvaluations(teamId, evaluations) {
  return request(`/teams/${teamId}/evaluations`, { method: 'POST', body: { evaluations } })
}
