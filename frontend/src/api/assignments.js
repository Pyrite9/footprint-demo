import { request } from './client'

// 과제와 조, 조장을 한 번에 만듭니다. assignment는 { title, startDate, endDate, leaderEmails }입니다.
// 성공하면 { id, title, startDate, endDate }를 돌려줍니다.
export function createAssignment(assignment) {
  return request('/assignments', { method: 'POST', body: assignment })
}
