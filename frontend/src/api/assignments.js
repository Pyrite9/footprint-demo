import { request } from './client'

// 과제와 조, 조장을 한 번에 만듭니다. assignment는 { title, startDate, endDate, leaderEmails }입니다.
// 성공하면 { id, title, startDate, endDate, phase, evaluationStartedAt }를 돌려줍니다.
export function createAssignment(assignment) {
  return request('/assignments', { method: 'POST', body: assignment })
}

// 내가 볼 수 있는 과제 목록 [{ id, title, startDate, endDate, phase, evaluationStartedAt }]을 가져옵니다.
export function getAssignments() {
  return request('/assignments')
}

// 과제 하나와 볼 수 있는 조 목록 { id, title, startDate, endDate, phase, evaluationStartedAt, professorName, teams: [{ id, name, leaderName }] }을 가져옵니다.
export function getAssignment(assignmentId) {
  return request(`/assignments/${assignmentId}`)
}

// 과제를 평가 단계로 바꿉니다. 과제를 만든 교수만 할 수 있습니다. 성공하면 과제 목록의 한 줄과 같은 모양을 돌려줍니다.
export function startEvaluation(assignmentId) {
  return request(`/assignments/${assignmentId}/start-evaluation`, { method: 'POST' })
}
