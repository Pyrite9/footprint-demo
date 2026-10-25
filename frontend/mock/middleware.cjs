// 가짜 API(json-server)에 실제 백엔드와 같은 규칙을 붙입니다. 주소마다 handle 함수 하나가 실제 API 하나를 흉내 냅니다.
// json-server 0.17.4가 이 파일을 require로 읽기 때문에 이 파일만 CommonJS(require, module.exports)로 씁니다.
// token은 "mock-token-<회원 id>" 문자열입니다. 실제 JWT와 달리 서명이 없으므로 화면은 token을 풀어 읽지 않습니다.
// 데이터는 db.json에 있습니다. req.app.db는 json-server가 db.json을 읽어 둔 lowdb이고, .write()를 부르면 파일에 저장됩니다.

const BEARER_TOKEN_PREFIX = 'Bearer mock-token-'
const NOT_VALID_MESSAGE = '입력값이 올바르지 않습니다'
const NOT_READABLE_MESSAGE = '요청 body의 형식이 올바르지 않습니다'
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

// 실제 백엔드의 ApiErrorResponse와 같은 모양 { code, message }로 오류를 보냅니다.
function sendError(res, status, code, message) {
  res.status(status).json({ code, message })
}

// 값이 없거나 공백뿐이면 true입니다. 백엔드의 @NotBlank와 같은 기준입니다.
function isBlank(value) {
  return typeof value !== 'string' || value.trim() === ''
}

// db.json의 한 목록에서 다음 id를 정합니다. 실제 DB의 자동 증가 번호를 흉내 냅니다.
function nextId(db, collection) {
  const items = db.get(collection).value()
  return items.length === 0 ? 1 : Math.max(...items.map((item) => item.id)) + 1
}

// Authorization header의 token으로 회원을 찾습니다. token이 없거나 틀리면 undefined입니다.
function findLoginMember(req) {
  const header = req.headers.authorization
  if (!header || !header.startsWith(BEARER_TOKEN_PREFIX)) return undefined

  const id = Number(header.substring(BEARER_TOKEN_PREFIX.length))
  return req.app.db.get('members').find({ id }).value()
}

// POST /auth/login: 이메일과 비밀번호를 확인하고 token을 돌려줍니다.
function handleLogin(req, res) {
  const { email, password } = req.body
  if (isBlank(email) || isBlank(password)) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }

  const member = req.app.db.get('members').find({ email }).value()
  // 실제 백엔드처럼 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 메시지를 줍니다.
  if (!member || member.password !== password) {
    return sendError(res, 401, 'UNAUTHORIZED', '이메일 또는 비밀번호가 올바르지 않습니다')
  }
  return res.json({ accessToken: 'mock-token-' + member.id })
}

// GET /members/me: 비밀번호를 뺀 내 정보를 돌려줍니다.
function handleMe(req, res, loginMember) {
  const { id, email, name, memberType } = loginMember
  return res.json({ id, email, name, memberType })
}

// POST /assignments: 교수가 과제, 조, 조장을 한 번에 만듭니다. 검사 순서는 실제 백엔드와 같습니다.
function handleCreateAssignment(req, res, loginMember) {
  const db = req.app.db
  const { title, startDate, endDate, leaderEmails } = req.body

  // 1. 날짜 형식이 틀리면 실제 백엔드는 JSON을 읽는 단계에서 거절합니다.
  if ((startDate != null && !DATE_PATTERN.test(startDate)) || (endDate != null && !DATE_PATTERN.test(endDate))) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_READABLE_MESSAGE)
  }
  // 2. @NotBlank @Size(max = 255), @NotNull, @NotEmpty 검사입니다.
  if (isBlank(title) || title.length > 255 || !startDate || !endDate || !Array.isArray(leaderEmails) || leaderEmails.length === 0) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  // 3. 여기부터는 Service의 검사입니다.
  if (loginMember.memberType !== 'PROFESSOR') {
    return sendError(res, 403, 'FORBIDDEN', '교수만 과제를 만들 수 있습니다')
  }
  // yyyy-MM-dd 문자열은 글자 순서와 날짜 순서가 같아서 문자열로 비교할 수 있습니다.
  if (endDate < startDate) {
    return sendError(res, 400, 'INVALID_INPUT', '종료일이 시작일보다 앞입니다')
  }

  const leaders = []
  // 이미 검사한 이메일입니다. 백엔드의 checkedEmails와 같습니다.
  const checkedEmails = new Set()
  for (const email of leaderEmails) {
    if (checkedEmails.has(email)) {
      return sendError(res, 400, 'INVALID_INPUT', '같은 이메일을 두 조의 조장으로 지정할 수 없습니다: ' + email)
    }
    checkedEmails.add(email)
    const leader = db.get('members').find({ email }).value()
    if (!leader) {
      return sendError(res, 400, 'INVALID_INPUT', '가입하지 않은 이메일입니다: ' + email)
    }
    if (leader.memberType !== 'STUDENT') {
      return sendError(res, 400, 'INVALID_INPUT', '학생이 아닌 회원은 조장이 될 수 없습니다: ' + email)
    }
    leaders.push(leader)
  }

  const assignment = { id: nextId(db, 'assignments'), title, startDate, endDate, professorId: loginMember.id }
  db.get('assignments').push(assignment).write()
  leaders.forEach((leader, index) => {
    const team = { id: nextId(db, 'teams'), assignmentId: assignment.id, name: index + 1 + '조', inviteCode: null }
    db.get('teams').push(team).write()
    db.get('teamMembers')
      .push({ id: nextId(db, 'teamMembers'), teamId: team.id, memberId: leader.id, leader: true, roleName: null, roleAssignedAt: null })
      .write()
  })

  return res.json({ id: assignment.id, title, startDate, endDate })
}

// 요청마다 실행됩니다. 주소에 맞는 handle 함수가 직접 응답합니다.
module.exports = function (req, res) {
  if (req.method === 'POST' && req.path === '/auth/login') {
    return handleLogin(req, res)
  }

  // 그 밖의 주소는 실제 백엔드처럼 token이 있어야 합니다.
  const loginMember = findLoginMember(req)
  if (!loginMember) {
    return sendError(res, 401, 'UNAUTHORIZED', '로그인이 필요합니다')
  }

  if (req.method === 'GET' && req.path === '/members/me') {
    return handleMe(req, res, loginMember)
  }
  if (req.method === 'POST' && req.path === '/assignments') {
    return handleCreateAssignment(req, res, loginMember)
  }

  // 실제 API에 없는 주소입니다. json-server의 기본 주소(/members 등)로 넘기면 비밀번호까지 그대로 보이므로 막습니다.
  return sendError(res, 404, 'NOT_FOUND', '없는 주소입니다')
}
