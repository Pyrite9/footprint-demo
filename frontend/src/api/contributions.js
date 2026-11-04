import { request } from './client'

// 조의 기여 항목 목록 [{ id, title, assigneeMemberId, assigneeName, createdAt, reportMemo, reportedAt, canceledAt }]을 최근에 등록한 것부터 가져옵니다.
export function getContributions(teamId) {
  return request(`/teams/${teamId}/contributions`)
}

// 조장이 항목명과 담당 조원(조 참여 번호)을 정해 기여 항목을 등록하고, 저장된 항목을 돌려줍니다.
export function createContribution(teamId, title, teamMemberId) {
  return request(`/teams/${teamId}/contributions`, {
    method: 'POST',
    body: { title, teamMemberId },
  })
}
