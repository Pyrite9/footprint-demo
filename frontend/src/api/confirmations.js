import { request } from './client'

// 산출물의 완료확인 이력 [{ id, deliverableId, contributionId, confirmerMemberId, confirmerName, confirmedAt, canceledAt }]을 최근 것부터 가져옵니다.
export function getDeliverableConfirmations(deliverableId) {
  return request(`/deliverables/${deliverableId}/confirmations`)
}

// 기여 항목의 완료확인 이력을 최근 것부터 가져옵니다. 모양은 산출물의 이력과 같습니다.
export function getContributionConfirmations(contributionId) {
  return request(`/contributions/${contributionId}/confirmations`)
}

// 산출물을 확인하고 저장된 확인을 돌려줍니다.
export function confirmDeliverable(deliverableId) {
  return request(`/deliverables/${deliverableId}/confirmations`, { method: 'POST' })
}

// 보고된 기여 항목을 확인하고 저장된 확인을 돌려줍니다.
export function confirmContribution(contributionId) {
  return request(`/contributions/${contributionId}/confirmations`, { method: 'POST' })
}
