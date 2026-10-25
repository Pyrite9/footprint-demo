import { request, requestBlob } from './client'

// 조의 산출물 목록 [{ id, roleName, fileName, fileSize, uploaderName, uploadedAt }]을 최신순으로 가져옵니다.
export function getDeliverables(teamId) {
  return request(`/teams/${teamId}/deliverables`)
}

// 파일 하나를 올리고 저장된 산출물을 돌려줍니다. 서버는 이름이 file인 칸을 읽습니다.
export function uploadDeliverable(teamId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request(`/teams/${teamId}/deliverables`, { method: 'POST', body: formData })
}

// 산출물 파일 본문을 Blob으로 가져옵니다. 저장은 화면이 합니다.
export function downloadDeliverable(deliverableId) {
  return requestBlob(`/deliverables/${deliverableId}/download`)
}
