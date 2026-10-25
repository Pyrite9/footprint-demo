// 로그인 token을 sessionStorage에 저장하고 꺼냅니다.
// sessionStorage는 탭마다 따로 있어서 탭마다 다른 계정으로 로그인할 수 있습니다. 탭을 닫으면 지워집니다.
const TOKEN_KEY = 'accessToken'

// 저장된 token을 돌려줍니다. 없으면 null입니다.
export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

// 로그인에 성공했을 때 token을 저장합니다.
export function saveToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token)
}

// 로그아웃하거나 token이 만료됐을 때 token을 지웁니다.
export function removeToken() {
  sessionStorage.removeItem(TOKEN_KEY)
}
