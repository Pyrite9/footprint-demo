import { request } from './client'

// 한 조의 기록을 최근 것부터 가져옵니다. 동료평가와 임시 저장은 오지 않습니다.
export function getTeamRecords(teamId) {
  return request(`/teams/${teamId}/records`)
}
