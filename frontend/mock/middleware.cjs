// 가짜 API(json-server)에 실제 백엔드와 같은 로그인 규칙을 붙입니다.
// json-server 0.17.4가 이 파일을 require로 읽기 때문에 이 파일만 CommonJS(require, module.exports)로 씁니다.
// token은 "mock-token-<회원 id>" 문자열입니다. 실제 JWT와 달리 서명이 없으므로 화면은 token을 풀어 읽지 않습니다.

const BEARER_TOKEN_PREFIX = 'Bearer mock-token-'

// 실제 백엔드의 ApiErrorResponse와 같은 모양 { code, message }로 오류를 보냅니다.
function sendError(res, status, code, message) {
  res.status(status).json({ code, message })
}

// 값이 없거나 공백뿐이면 true입니다. 백엔드의 @NotBlank와 같은 기준입니다.
function isBlank(value) {
  return typeof value !== 'string' || value.trim() === ''
}

// Authorization header의 token으로 회원을 찾습니다. token이 없거나 틀리면 undefined입니다.
function findLoginMember(req) {
  const header = req.headers.authorization
  if (!header || !header.startsWith(BEARER_TOKEN_PREFIX)) return undefined

  const id = Number(header.substring(BEARER_TOKEN_PREFIX.length))
  // req.app.db는 json-server가 db.json을 읽어 둔 데이터입니다.
  return req.app.db.get('members').find({ id }).value()
}

// 요청마다 실행됩니다. 여기서 직접 응답하거나, next()로 json-server의 기본 처리에 넘깁니다.
module.exports = function (req, res, next) {
  // POST /auth/login: token 없이 부를 수 있습니다.
  if (req.method === 'POST' && req.path === '/auth/login') {
    const { email, password } = req.body
    if (isBlank(email) || isBlank(password)) {
      return sendError(res, 400, 'INVALID_INPUT', '입력값이 올바르지 않습니다')
    }

    const member = req.app.db.get('members').find({ email }).value()
    // 실제 백엔드처럼 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 메시지를 줍니다.
    if (!member || member.password !== password) {
      return sendError(res, 401, 'UNAUTHORIZED', '이메일 또는 비밀번호가 올바르지 않습니다')
    }
    return res.json({ accessToken: 'mock-token-' + member.id })
  }

  // 그 밖의 주소는 실제 백엔드처럼 token이 있어야 합니다.
  const loginMember = findLoginMember(req)
  if (!loginMember) {
    return sendError(res, 401, 'UNAUTHORIZED', '로그인이 필요합니다')
  }

  // GET /members/me: 비밀번호를 뺀 내 정보를 돌려줍니다.
  if (req.method === 'GET' && req.path === '/members/me') {
    const { id, email, name, memberType } = loginMember
    return res.json({ id, email, name, memberType })
  }

  next()
}
