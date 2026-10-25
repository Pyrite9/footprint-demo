// 동료평가 화면 위쪽에 고정하는 문구입니다. 시스템은 근거만 보여 주고 판단은 사람이 한다는 원칙입니다.
export const EVALUATION_NOTICES = [
  '업로드 횟수는 기여도가 아닙니다',
  '점수는 시스템이 계산하지 않습니다',
]

// 신태섭(2018) 조별 과제 동료평가 행동기준평정척도입니다. 문장은 그대로 두고 고치지 않습니다.
// key는 서버에 보내는 점수 필드 이름입니다(POST /teams/{teamId}/evaluations). 2점과 4점은 인접한 두 기준의 중간입니다.
export const EVALUATION_CRITERIA = [
  {
    key: 'goalSharing',
    name: '목표 공유·협업',
    score5: '다른 조원과 같은 목표를 공유하며 협업을 잘 이루어냄',
    score3: '목표는 공유했지만 협업이 잘 이루어지지 않음',
    score1: '협업 없이 혼자서 과제를 진행함',
  },
  {
    key: 'roleFulfillment',
    name: '역할 수행',
    score5: '맡은 역할을 책임감 있게 수행해 과제에 큰 도움이 됨',
    score3: '맡은 역할에 최소한의 노력만 들여 과제에 별로 도움이 되지 않음',
    score1: '맡은 역할을 하지 않아 과제에 피해를 줌(예: 연락 두절)',
  },
  {
    key: 'encouragement',
    name: '격려·도움',
    score5: '다른 조원을 격려하고 돕기 위해 적극적으로 노력함',
    score3: '격려하거나 돕지는 않았지만 피해를 주지는 않음',
    score1: '돕지 않고 조원의 사기를 떨어뜨려 피해를 줌',
  },
  {
    key: 'onlineCommunication',
    name: '온라인 소통',
    score5: '카카오톡 등 온라인에서 적극적으로 소통함',
    score3: '온라인에서 소극적으로 소통함',
    score1: '온라인 소통에 불참하거나 불성실함',
  },
  {
    key: 'offlineCommunication',
    name: '대면 소통',
    score5: '대면 회의에서 적극적으로 소통함',
    score3: '대면 회의에서 소극적으로 소통함',
    score1: '대면 회의에 불참하거나 불성실함',
  },
  {
    key: 'progressCheck',
    name: '진행 점검·보충',
    score5: '진행 과정에 계속 관심을 두고 부족한 부분을 보충하려 노력함',
    score3: '관심은 있었지만 부족한 부분을 보충하려 하지 않음',
    score1: '진행에 관심이 없고 발전시키려는 노력을 하지 않음',
  },
]

// 고를 수 있는 점수입니다. 높은 점수부터 보여 줍니다.
export const SCORE_OPTIONS = [5, 4, 3, 2, 1]

// 점수 하나의 설명 문장을 돌려줍니다. 2점과 4점은 기준 문장이 없어서 "중간"으로 적습니다.
export function describeScore(criterion, score) {
  if (score === 5) return criterion.score5
  if (score === 4) return '5점과 3점의 중간'
  if (score === 3) return criterion.score3
  if (score === 2) return '3점과 1점의 중간'
  return criterion.score1
}
