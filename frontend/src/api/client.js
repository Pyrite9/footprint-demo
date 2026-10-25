import { getToken, removeToken } from './token'

const API_URL = import.meta.env.VITE_API_URL

// 서버가 { code, message } 모양으로 답하지 않았을 때 보여 줄 메시지입니다. (예: 서버가 꺼져 있음, 500 오류)
const DEFAULT_ERROR_MESSAGE = '요청을 처리하지 못했습니다'

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

// 주소 붙이기, token 붙이기, 실패 처리를 한 곳에서 합니다. 성공하면 fetch의 응답(res)을 그대로 넘깁니다.
function send(path, options) {
  const token = getToken()
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

  return fetch(`${API_URL}${path}`, { method: options.method ?? 'GET', headers, body })
    .catch(() => {
      // 서버가 꺼져 있거나 주소가 틀려 응답 자체를 받지 못한 경우입니다. 브라우저의 영어 메시지 대신 기본 메시지를 씁니다.
      throw createError(DEFAULT_ERROR_MESSAGE, null, null)
    })
    .then((res) => {
      if (res.ok) {
        return res
      }

      // token을 보냈는데 401이면 token이 만료됐거나 틀린 것입니다. token을 지우고 로그인 화면을 새로 엽니다.
      // 이 파일은 React 컴포넌트가 아니라서 useNavigate를 쓸 수 없습니다.
      if (res.status === 401 && token) {
        removeToken()
        window.location.assign('/login')
      }

      // 실패 응답의 body를 읽어 status와 code를 담은 오류를 던집니다. 화면의 .catch가 이 오류를 받습니다.
      // body가 JSON이 아니면 빈 객체로 봅니다. code가 없으면 우리 서버의 ApiErrorResponse가 아니므로 기본 메시지를 씁니다.
      return res.json()
        .catch(() => ({}))
        .then((data) => {
          throw createError(data.code ? data.message : DEFAULT_ERROR_MESSAGE, res.status, data.code ?? null)
        })
    })
}

// 화면에 보여 줄 message와 함께 status, code를 담은 오류를 만듭니다.
function createError(message, status, code) {
  const error = new Error(message)
  error.status = status
  error.code = code
  return error
}
