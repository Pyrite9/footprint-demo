import { request } from './client'

// 조의 보고서 목록 [{ id, title, createdAt }]을 최근에 만든 것부터 가져옵니다.
export function getReports(teamId) {
  return request(`/teams/${teamId}/reports`)
}

// 조장이 제목으로 보고서를 만들고, 저장된 보고서를 돌려줍니다.
export function createReport(teamId, title) {
  return request(`/teams/${teamId}/reports`, { method: 'POST', body: { title } })
}

// 보고서의 파트 목록 [{ id, title, assigneeMemberId, assigneeName, createdAt, canceledAt }]을 먼저 등록한 것부터 가져옵니다.
export function getReportParts(reportId) {
  return request(`/reports/${reportId}/parts`)
}

// 조장이 파트 제목과 담당 조원(조 참여 번호)을 정해 파트를 등록하고, 저장된 파트를 돌려줍니다.
export function createReportPart(reportId, title, teamMemberId) {
  return request(`/reports/${reportId}/parts`, { method: 'POST', body: { title, teamMemberId } })
}

// 조장이 파트를 취소하고, 취소 시각이 담긴 파트를 돌려줍니다.
export function cancelReportPart(partId) {
  return request(`/report-parts/${partId}/cancel`, { method: 'POST' })
}
