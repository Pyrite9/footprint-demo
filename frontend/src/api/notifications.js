import { request } from './client'

// 로그인한 회원이 받은 알림 목록 [{ id, type, message, teamId, createdAt, readAt }]을 최근 것부터 가져옵니다.
export function getNotifications() {
  return request('/notifications')
}

// 받은 알림 하나를 읽음으로 표시하고, 읽은 시각이 담긴 알림을 돌려줍니다. 이미 읽었으면 처음 읽은 시각 그대로 돌아옵니다.
export function readNotification(notificationId) {
  return request(`/notifications/${notificationId}/read`, { method: 'POST' })
}
