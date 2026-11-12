// 로그인 access token을 이 모듈의 변수(메모리)에 두고 꺼냅니다. sessionStorage·localStorage에는 넣지 않습니다.
// 화면에 끼어든 스크립트가 저장소에서 token을 꺼내 가지 못하게 하려는 것입니다(docs/api-draft.md "access token").
// 새로고침하거나 탭을 새로 열면 변수가 비어서, RequireLogin이 refresh token(cookie)으로 다시 받아 옵니다.
// refresh token cookie는 같은 브라우저의 탭이 함께 써서, 탭마다 다른 계정으로 로그인할 수 없습니다.
let accessToken = null

// 저장된 token을 돌려줍니다. 없으면 null입니다.
export function getToken() {
  return accessToken
}

// 로그인이나 재발급에 성공했을 때 token을 저장합니다.
export function saveToken(token) {
  accessToken = token
}

// 로그아웃하거나 로그인이 만료됐을 때 token을 지웁니다.
export function removeToken() {
  accessToken = null
}
