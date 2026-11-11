// 서버가 준 값을 화면에 보여 줄 글자로 바꾸는 함수들입니다. 여러 화면이 같이 씁니다.

// 목록에 넣는 짧은 시각입니다. 예: '2026-11-04T14:30:15' → '11.04 14:30'
export function formatShortDateTime(dateTime) {
  return `${dateTime.slice(5, 7)}.${dateTime.slice(8, 10)} ${dateTime.slice(11, 16)}`
}

// 오른쪽 상세에 넣는 전체 시각입니다. 예: '2026-11-04T14:30:15.123456' → '2026-11-04 14:30:15'
export function formatDateTime(dateTime) {
  return dateTime.slice(0, 19).replace('T', ' ')
}

// byte 수를 읽기 쉬운 단위로 바꿉니다. 예: 52344 → "51.1 KB"
export function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

// 동그라미에 넣는 이름 뒤 두 글자입니다. 예: '김민준' → '민준'
export function initialOf(name) {
  return name.slice(-2)
}

// 과제 레일 칸에 넣을 두 글자입니다. 단어가 둘 이상이면 앞 두 단어의 첫 글자를, 한 단어면 앞 두 글자를 씁니다.
// 앞 두 글자만 쓰면 "발자국 데모 과제"와 "발자국 평가 과제"가 같아 보이기 때문입니다.
export function railLabelOf(title) {
  const words = title.trim().split(/\s+/)
  return words.length >= 2 ? words[0][0] + words[1][0] : title.slice(0, 2)
}

// 역할 이름으로 색 class를 정합니다. 글자 코드를 더해 5로 나눈 나머지라서, 같은 이름은 어느 화면에서나 같은 색입니다.
// 색은 역할을 구분할 뿐이고 순서나 순위를 뜻하지 않습니다. 역할이 없으면 class를 붙이지 않아 회색 점이 됩니다.
export function roleColorClass(roleName) {
  if (roleName === null) return ''
  let sum = 0
  for (const char of roleName) {
    sum += char.codePointAt(0)
  }
  return `rc${(sum % 5) + 1}`
}

// 역할 줄의 이름을 모읍니다. 지금 맡은 역할과 올린 파일의 역할을 합칩니다. 파일에는 올릴 때의 역할명이 저장되어서, 지금 맡은 사람이 없어도 그 역할로 올린 파일이 있으면 줄이 남습니다.
// 순서는 가나다순으로 고정합니다. 많이 올린 순이나 최근 순으로 두면 역할끼리 비교하는 순위처럼 보입니다.
export function roleNamesOf(members, deliverables) {
  const currentRoleNames = members.filter((member) => member.roleName !== null).map((member) => member.roleName)
  const uploadedRoleNames = deliverables.map((deliverable) => deliverable.roleName)
  return [...new Set([...currentRoleNames, ...uploadedRoleNames])].sort((a, b) => a.localeCompare(b))
}

// 이 시각이 과제 기간 밖이면 true입니다. 서버는 기간이 지나도 기록을 막지 않아서 기간 밖 업로드가 있을 수 있습니다.
export function isOutsidePeriod(dateTime, startDate, endDate) {
  const date = dateTime.slice(0, 10)
  return date < startDate || date > endDate
}

// 진척 화면에서 점을 놓을 가로 위치(0~100)입니다. 기간 밖이면 가까운 쪽 끝입니다. 위치에만 쓰고 글자로 보여 주지 않습니다. 진척률이 아닙니다.
export function timelinePercentOf(dateTime, startDate, endDate) {
  // 날짜만 쓴 글자는 new Date가 UTC로 읽습니다. 시각을 붙여야 dateTime처럼 브라우저 시간대로 읽습니다.
  const start = new Date(`${startDate}T00:00:00`).getTime()
  const end = new Date(`${endDate}T23:59:59`).getTime()
  const percent = ((new Date(dateTime.slice(0, 19)).getTime() - start) / (end - start)) * 100
  return Math.min(100, Math.max(0, percent))
}

// 같은 역할의 산출물 중 이 산출물보다 먼저(또는 같이) 올린 것의 개수가 순번입니다. id가 작을수록 먼저 올린 것입니다.
// 삭제된 산출물도 세어서 순번이 끊기지 않습니다. 목록에서 계산할 수 있는 값이라 서버에도 state에도 두지 않습니다.
export function sequenceOf(deliverables, target) {
  return deliverables.filter((deliverable) => deliverable.roleName === target.roleName && deliverable.id <= target.id).length
}

// 기여 항목의 상태 이름을 시각으로 정합니다. 서버에도 state에도 두지 않습니다.
export function contributionStatusOf(contribution) {
  if (contribution.canceledAt !== null) return '취소됨'
  if (contribution.reportedAt !== null) return '보고됨'
  return '보고 전'
}

// 기여 항목 상태마다 꼬리표 색 class입니다.
export const CONTRIBUTION_STATUS_PILLS = {
  '보고 전': 'amber',
  '보고됨': 'green',
  '취소됨': 'gray',
}

// 기록 열람의 줄 종류마다 보여 줄 이름입니다. 기여 항목의 등록과 취소는 조장이 하지만 줄의 이름은 담당 조원이라서 "담당"을 붙입니다.
export const RECORD_TYPE_LABELS = {
  DELIVERABLE_UPLOADED: '산출물 업로드',
  DELIVERABLE_DELETED: '산출물 삭제',
  CONTRIBUTION_CREATED: '담당 기여 항목 등록',
  CONTRIBUTION_REPORTED: '완료 보고',
  CONTRIBUTION_CANCELED: '담당 기여 항목 취소',
  CONFIRMATION_CREATED: '완료확인',
  CONFIRMATION_CANCELED: '완료확인 취소',
  ROLE_ASSIGNED: '역할 지정',
  ROLE_CHANGED: '역할 변경',
  LEADER_CHANGED: '조장으로 교체',
  EVALUATION_STARTED: '평가 시작. 기록 단계에서 평가 단계로 바뀜',
}

// 알림 종류마다 보여 줄 이름입니다. 값은 서버의 NotificationType과 같습니다.
export const NOTIFICATION_TYPE_LABELS = {
  EVALUATION_STARTED: '평가 시작',
  CONTRIBUTION_ASSIGNED: '기여 항목 담당',
  CONTRIBUTION_REPORTED: '완료 보고',
}

// 알림을 누르면 갈 주소입니다. 평가 시작은 그 조의 동료평가 화면, 나머지는 그 조의 기여 항목 화면입니다.
export function notificationPathOf(notification) {
  if (notification.type === 'EVALUATION_STARTED') return `/teams/${notification.teamId}/evaluation`
  return `/teams/${notification.teamId}/contributions`
}

// 과제 단계마다 꼬리표 색 class입니다.
export const PHASE_PILLS = {
  RECORDING: 'blue',
  EVALUATION: 'amber',
}
