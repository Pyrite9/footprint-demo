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

// 과제 단계마다 꼬리표 색 class입니다.
export const PHASE_PILLS = {
  RECORDING: 'blue',
  EVALUATION: 'amber',
}
