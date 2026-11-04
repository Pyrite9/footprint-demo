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

// 담당 조원이 완료 보고하고, 메모와 보고 시각이 담긴 항목을 돌려줍니다.
export function reportContribution(contributionId, memo) {
  return request(`/contributions/${contributionId}/report`, { method: 'POST', body: { memo } })
}

// 조장이 보고 전 항목을 취소하고, 취소 시각이 담긴 항목을 돌려줍니다.
export function cancelContribution(contributionId) {
  return request(`/contributions/${contributionId}/cancel`, { method: 'POST' })
}
