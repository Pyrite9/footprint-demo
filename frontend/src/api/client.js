import { getToken, removeToken, saveToken } from './token'

const API_URL = import.meta.env.VITE_API_URL

// 서버가 { code, message } 모양으로 답하지 않았을 때 보여 줄 메시지입니다. (예: 서버가 꺼져 있어 응답 자체가 없음)
const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했습니다'
// 재발급의 차례를 지키는 Web Locks의 이름입니다. 같은 화면 주소(origin)의 모든 탭이 이 이름 하나를 함께 씁니다.
const REFRESH_LOCK_NAME = 'footprint-refresh-token'

// 지금 진행 중인 재발급입니다. 없으면 null입니다.
// 한 화면이 요청 여러 개를 동시에 보냈다가 모두 401을 받아도, 재발급은 하나만 보내고 나머지는 이 Promise를 함께 기다립니다.
let refreshing = null

// 모든 API 호출이 거치는 공통 함수입니다. 응답 body를 JSON으로 읽어 돌려줍니다.
// options는 { method, body }입니다. body가 FormData면 그대로 보내고, 객체면 JSON으로 바꿔 보냅니다.
export function request(path, options = {}) {
  return send(path, options).then((res) => {
    // 204처럼 body가 없는 성공 응답은 null을 돌려줍니다.
    return res.status === 204 ? null : res.json()
  })
}

// 파일을 내려받을 때 쓰는 공통 함수입니다. 응답 body를 JSON이 아니라 Blob(파일 내용 덩어리)으로 돌려줍니다.
// <a href>로 바로 열면 token header를 붙일 수 없어서, fetch로 받은 뒤 화면에서 저장합니다.
export function requestBlob(path) {
  return send(path, {}).then((res) => res.blob())
}

// refresh token(cookie)으로 새 access token을 받아 저장합니다. 성공하면 아무것도 돌려주지 않고, 실패하면 오류를 던집니다.
// expiredToken은 401을 받은 요청이 보냈던 access token입니다. 화면을 처음 열 때(RequireLogin)는 null입니다.
// 이 탭에서 이미 진행 중인 재발급이 있으면 새로 보내지 않고 그것을 함께 기다립니다.
export function refreshAccessToken(expiredToken = null) {
  if (refreshing === null) {
    // finally: 성공하든 실패하든 끝나면 비워서, 다음 만료 때 새로 재발급하게 합니다.
    refreshing = withRefreshLock(() => requestNewAccessToken(expiredToken)).finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

// 요청 하나를 보내는 흐름입니다.
// 1. 지금 access token을 붙여 보냅니다(sendOnce).
// 2. token을 보냈는데 401이면 access token이 만료된 것입니다. 재발급(refreshAccessToken)하고 같은 요청을 한 번만 다시 보냅니다.
// 3. 재발급이 401이면 refresh token도 끝난 것이라 로그인 화면으로 갑니다. 다시 보낸 요청이 또 401이어도 같습니다.
// 4. 성공하면 fetch의 응답(res)을 그대로 넘기고, 실패하면 status와 code를 담은 오류를 던집니다(toResponseOrError).
function send(path, options) {
  const token = getToken()

  return sendOnce(path, options, token).then((res) => {
    if (res.status !== 401 || token === null) {
      return toResponseOrError(res)
    }

    return refreshAccessToken(token)
      .catch((err) => {
        if (err.status === 401) {
          goToLogin()
        }
        throw err
      })
      .then(() => sendOnce(path, options, getToken()))
      .then((retried) => {
        if (retried.status === 401) {
          goToLogin()
        }
        return toResponseOrError(retried)
      })
  })
}

// 주소 붙이기, token 붙이기, body 바꾸기를 하고 fetch를 한 번 보냅니다. 응답(res)을 성공·실패 구분 없이 돌려줍니다.
// 같은 요청을 다시 보낼 수 있게 options를 고치지 않습니다. FormData와 객체 body는 몇 번이든 다시 보낼 수 있습니다.
function sendOnce(path, options, token) {
  const headers = {}
  let body = options.body

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  // FormData는 브라우저가 Content-Type(파일 구분선 포함)을 직접 붙여야 해서 여기서 붙이지 않습니다.
  if (body !== undefined && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(body)
  }

  // credentials: 'include': refresh token cookie를 주고받습니다. 같은 주소의 /api는 기본값으로도 실리지만,
  // 가짜 API(localhost:3001)는 화면과 주소가 달라서 이 값이 있어야 cookie가 실립니다.
  return fetch(`${API_URL}${path}`, { method: options.method ?? 'GET', headers, body, credentials: 'include' })
    .catch(() => {
      // 서버가 꺼져 있거나 주소가 틀려 응답 자체를 받지 못한 경우입니다. 브라우저의 영어 메시지 대신 기본 메시지를 씁니다.
      throw createError(DEFAULT_ERROR_MESSAGE, null, null)
    })
}

// 성공 응답은 그대로 돌려주고, 실패 응답은 body를 읽어 status와 code를 담은 오류를 던집니다. 화면의 .catch가 이 오류를 받습니다.
// body가 JSON이 아니면 빈 객체로 봅니다. code가 없으면 우리 서버의 ApiErrorResponse가 아니므로 기본 메시지를 씁니다.
function toResponseOrError(res) {
  if (res.ok) {
    return res
  }
  return res.json()
    .catch(() => ({}))
    .then((data) => {
      throw createError(data.code ? data.message : DEFAULT_ERROR_MESSAGE, res.status, data.code ?? null)
    })
}

// POST /auth/refresh를 보내 새 access token을 저장합니다. cookie는 브라우저가 붙이므로 access token은 붙이지 않습니다.
// 재발급이 401이면 이 탭의 access token도 지웁니다.
function requestNewAccessToken(expiredToken) {
  // 차례를 기다리는 동안 이 탭이 이미 새 token을 받았으면 다시 받지 않습니다.
  // (401을 받은 요청이 늦게 돌아와, 앞선 재발급이 이미 끝난 뒤에 여기로 온 경우입니다.)
  const current = getToken()
  if (expiredToken !== null && current !== null && current !== expiredToken) {
    return Promise.resolve()
  }

  return sendOnce('/auth/refresh', { method: 'POST' }, null)
    .then(toResponseOrError)
    .then((res) => res.json())
    .then((data) => {
      saveToken(data.accessToken)
    })
    .catch((err) => {
      if (err.status === 401) {
        removeToken()
      }
      throw err
    })
}

// 재발급을 탭끼리 한 번에 하나씩 하게 합니다. Web Locks API(navigator.locks)를 씁니다.
// 이유: refresh token은 쓰면 바뀝니다(교체). 탭 두 개가 같은 값으로 동시에 재발급하면 늦은 쪽이 401을 받아 로그인 화면으로 튕깁니다.
// navigator.locks.request(이름, 함수)는 같은 이름의 lock을 쥔 탭이 없을 때 함수를 실행하고, 함수의 Promise가 끝나면 lock을 놓습니다.
// 뒤의 탭은 앞의 탭이 바꿔 둔 새 cookie로 재발급하므로 둘 다 성공합니다.
// Web Locks는 https나 localhost에서만 있습니다. 없는 환경(예: http로 연 다른 주소)에서는 차례 없이 바로 실행합니다.
function withRefreshLock(task) {
  if (!navigator.locks) {
    return task()
  }
  return navigator.locks.request(REFRESH_LOCK_NAME, task)
}

// access token을 지우고 로그인 화면을 새로 엽니다. 화면에는 "로그인이 만료되었습니다" 안내가 나옵니다.
// 이 파일은 React 컴포넌트가 아니라서 useNavigate를 쓸 수 없습니다.
function goToLogin() {
  removeToken()
  window.location.assign('/login?expired=1')
}

// 화면에 보여 줄 message와 함께 status, code를 담은 오류를 만듭니다.
function createError(message, status, code) {
  const error = new Error(message)
  error.status = status
  error.code = code
  return error
}
