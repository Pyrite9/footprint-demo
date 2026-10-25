// 가짜 API(json-server)에 실제 백엔드와 같은 규칙을 붙입니다. 주소마다 handle 함수 하나가 실제 API 하나를 흉내 냅니다.
// json-server 0.17.4가 이 파일을 require로 읽기 때문에 이 파일만 CommonJS(require, module.exports)로 씁니다.
// token은 "mock-token-<회원 id>" 문자열입니다. 실제 JWT와 달리 서명이 없으므로 화면은 token을 풀어 읽지 않습니다.
// 데이터는 db.json에 있습니다. req.app.db는 json-server가 db.json을 읽어 둔 lowdb이고, .write()를 부르면 파일에 저장됩니다.

// crypto는 Node.js에 들어 있는 모듈입니다. 초대 코드(UUID)를 만들 때 씁니다.
const crypto = require('crypto')

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

// 과제 목록과 상세에 쓰는 과제 모양 { id, title, startDate, endDate }입니다. 실제 AssignmentResponse와 같습니다.
function toAssignmentResponse(assignment) {
  const { id, title, startDate, endDate } = assignment
  return { id, title, startDate, endDate }
}

// GET /assignments: 교수는 만든 과제, 학생은 참여한 과제를 최신순으로 돌려줍니다.
function handleGetAssignments(req, res, loginMember) {
  const db = req.app.db
  if (loginMember.memberType === 'PROFESSOR') {
    const assignments = db.get('assignments').filter({ professorId: loginMember.id }).sortBy('id').reverse().value()
    return res.json(assignments.map(toAssignmentResponse))
  }
  // 조 참여 기록(teamMembers) → 조(teams) → 과제(assignments) 순서로 번호를 따라갑니다.
  const myTeamMembers = db.get('teamMembers').filter({ memberId: loginMember.id }).sortBy('id').reverse().value()
  const assignments = myTeamMembers.map((teamMember) => {
    const team = db.get('teams').find({ id: teamMember.teamId }).value()
    return db.get('assignments').find({ id: team.assignmentId }).value()
  })
  return res.json(assignments.map(toAssignmentResponse))
}

// GET /assignments/{assignmentId}: 과제와 볼 수 있는 조 목록을 돌려줍니다. 교수는 조 전부, 학생은 자기 조만입니다.
function handleGetAssignment(req, res, loginMember, assignmentId) {
  const db = req.app.db
  const assignment = db.get('assignments').find({ id: assignmentId }).value()
  if (!assignment) {
    return sendError(res, 404, 'NOT_FOUND', '과제가 없습니다. id=' + assignmentId)
  }

  let teams = db.get('teams').filter({ assignmentId }).sortBy('id').value()
  if (assignment.professorId !== loginMember.id) {
    const teamIds = teams.map((team) => team.id)
    const myTeamMember = db.get('teamMembers').find((teamMember) => teamMember.memberId === loginMember.id && teamIds.includes(teamMember.teamId)).value()
    if (!myTeamMember) {
      return sendError(res, 403, 'FORBIDDEN', '이 과제에 접근할 권한이 없습니다')
    }
    teams = teams.filter((team) => team.id === myTeamMember.teamId)
  }

  const professor = db.get('members').find({ id: assignment.professorId }).value()
  const teamSummaries = teams.map((team) => {
    const leaderTeamMember = db.get('teamMembers').find({ teamId: team.id, leader: true }).value()
    const leader = db.get('members').find({ id: leaderTeamMember.memberId }).value()
    return { id: team.id, name: team.name, leaderName: leader.name }
  })
  return res.json({ ...toAssignmentResponse(assignment), professorName: professor.name, teams: teamSummaries })
}

// 조원 목록의 조원 한 명 모양 { id, memberId, name, roleName, leader }입니다. 실제 TeamMemberResponse와 같습니다.
function toTeamMemberResponse(db, teamMember) {
  const member = db.get('members').find({ id: teamMember.memberId }).value()
  return { id: teamMember.id, memberId: member.id, name: member.name, roleName: teamMember.roleName, leader: teamMember.leader }
}

// 조 정보 모양 { id, name, assignmentId, assignmentTitle }입니다. 실제 TeamResponse와 같습니다.
function toTeamResponse(db, team) {
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  return { id: team.id, name: team.name, assignmentId: assignment.id, assignmentTitle: assignment.title }
}

// 실제 TeamAccessChecker.checkTeamAccess와 같습니다. 그 조의 조원이거나 그 과제를 만든 교수이면 true입니다.
// 아니면 403을 보내고 false를 돌려줍니다. 부른 쪽은 false이면 바로 끝냅니다.
function checkTeamAccess(db, res, team, loginMember) {
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  if (assignment.professorId === loginMember.id) return true
  return checkTeamMember(db, res, team, loginMember) !== null
}

// 실제 TeamAccessChecker.checkTeamMember와 같습니다. 조원이면 조 참여 기록, 아니면 403을 보내고 null입니다.
function checkTeamMember(db, res, team, loginMember) {
  const teamMember = db.get('teamMembers').find({ teamId: team.id, memberId: loginMember.id }).value()
  if (!teamMember) {
    sendError(res, 403, 'FORBIDDEN', '이 조에 접근할 권한이 없습니다')
    return null
  }
  return teamMember
}

// 실제 TeamAccessChecker.checkLeader와 같습니다. 조장이면 true, 아니면 403을 보내고 false입니다.
function checkLeader(db, res, team, loginMember) {
  const teamMember = db.get('teamMembers').find({ teamId: team.id, memberId: loginMember.id }).value()
  if (!teamMember || !teamMember.leader) {
    sendError(res, 403, 'FORBIDDEN', '조장만 할 수 있습니다')
    return false
  }
  return true
}

// 조를 찾습니다. 없으면 404를 보내고 undefined입니다.
function findTeamOrSendError(db, res, teamId) {
  const team = db.get('teams').find({ id: teamId }).value()
  if (!team) sendError(res, 404, 'NOT_FOUND', '조가 없습니다. id=' + teamId)
  return team
}

// GET /teams/{teamId}: 조 정보를 돌려줍니다.
function handleGetTeam(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkTeamAccess(db, res, team, loginMember)) return
  return res.json(toTeamResponse(db, team))
}

// GET /teams/{teamId}/members: 조원 목록을 합류한 순서로 돌려줍니다.
function handleGetTeamMembers(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkTeamAccess(db, res, team, loginMember)) return
  const teamMembers = db.get('teamMembers').filter({ teamId }).sortBy('id').value()
  return res.json(teamMembers.map((teamMember) => toTeamMemberResponse(db, teamMember)))
}

// GET /teams/{teamId}/invite-code: 조장에게 지금 유효한 초대 코드를 돌려줍니다.
function handleGetInviteCode(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkLeader(db, res, team, loginMember)) return
  return res.json({ inviteCode: team.inviteCode })
}

// POST /teams/{teamId}/invite-code: 조장이 초대 코드를 새로 발급합니다. 이전 코드는 무효가 됩니다.
function handleIssueInviteCode(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkLeader(db, res, team, loginMember)) return
  // 실제 백엔드처럼 UUID를 만듭니다.
  const inviteCode = crypto.randomUUID()
  db.get('teams').find({ id: teamId }).assign({ inviteCode }).write()
  return res.json({ inviteCode })
}

// POST /teams/join: 초대 코드로 조에 합류합니다. 이미 그 조의 조원이면 그대로 그 조를 돌려줍니다.
function handleJoinTeam(req, res, loginMember) {
  const db = req.app.db
  const { inviteCode } = req.body
  if (isBlank(inviteCode)) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  const team = db.get('teams').find({ inviteCode }).value()
  if (!team) {
    return sendError(res, 404, 'NOT_FOUND', '초대 코드가 올바르지 않습니다')
  }
  if (loginMember.memberType !== 'STUDENT') {
    return sendError(res, 403, 'FORBIDDEN', '학생만 조에 합류할 수 있습니다')
  }

  // 같은 과제의 조들 중 하나에 이미 있는지 봅니다.
  const sameAssignmentTeamIds = db.get('teams').filter({ assignmentId: team.assignmentId }).map('id').value()
  const existing = db.get('teamMembers')
    .find((teamMember) => teamMember.memberId === loginMember.id && sameAssignmentTeamIds.includes(teamMember.teamId))
    .value()
  if (existing) {
    if (existing.teamId === team.id) return res.json(toTeamResponse(db, team))
    return sendError(res, 409, 'DUPLICATE', '이미 이 과제의 다른 조에 있습니다')
  }

  db.get('teamMembers')
    .push({ id: nextId(db, 'teamMembers'), teamId: team.id, memberId: loginMember.id, leader: false, roleName: null, roleAssignedAt: null })
    .write()
  return res.json(toTeamResponse(db, team))
}

// POST /teams/{teamId}/members/{teamMemberId}/role: 조장이 역할이 없는 조원에게 역할을 지정합니다.
function handleAssignRole(req, res, loginMember, teamId, teamMemberId) {
  const db = req.app.db
  const { roleName } = req.body
  // @NotBlank @Size(max = 255) 검사입니다. 실제 백엔드처럼 권한 검사보다 먼저입니다.
  if (isBlank(roleName) || roleName.length > 255) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkLeader(db, res, team, loginMember)) return

  const target = db.get('teamMembers').find({ id: teamMemberId, teamId }).value()
  if (!target) {
    return sendError(res, 404, 'NOT_FOUND', '이 조의 조원이 아닙니다. id=' + teamMemberId)
  }
  if (target.roleName !== null) {
    return sendError(res, 409, 'DUPLICATE', '이미 역할이 지정된 조원입니다')
  }

  // 실제 백엔드처럼 지정 시각은 서버(가짜 API)의 현재 시각입니다.
  db.get('teamMembers').find({ id: teamMemberId }).assign({ roleName: roleName.trim(), roleAssignedAt: new Date().toISOString() }).write()
  const updated = db.get('teamMembers').find({ id: teamMemberId }).value()
  return res.json(toTeamMemberResponse(db, updated))
}

// 주소가 pattern과 맞으면 {id} 자리의 글자를 돌려줍니다. 맞지 않으면 null입니다.
// 예: matchId(/^\/assignments\/([^/]+)$/, '/assignments/3') → '3'(글자). 숫자 검사는 parseIdOrSendError가 합니다.
function matchId(pattern, path) {
  const match = path.match(pattern)
  return match ? match[1] : null
}

// 실제 백엔드처럼 주소의 {id} 자리가 숫자가 아니면 400을 보냅니다. 숫자이면 숫자로 바꿔 돌려줍니다.
function parseIdOrSendError(res, value) {
  if (!/^\d+$/.test(value)) {
    sendError(res, 400, 'INVALID_INPUT', '주소의 값이 올바르지 않습니다')
    return null
  }
  return Number(value)
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
  if (req.method === 'GET' && req.path === '/assignments') {
    return handleGetAssignments(req, res, loginMember)
  }

  const assignmentIdText = matchId(/^\/assignments\/([^/]+)$/, req.path)
  if (req.method === 'GET' && assignmentIdText !== null) {
    const assignmentId = parseIdOrSendError(res, assignmentIdText)
    if (assignmentId === null) return
    return handleGetAssignment(req, res, loginMember, assignmentId)
  }

  if (req.method === 'POST' && req.path === '/teams/join') {
    return handleJoinTeam(req, res, loginMember)
  }

  const teamIdText = matchId(/^\/teams\/([^/]+)$/, req.path)
  if (req.method === 'GET' && teamIdText !== null) {
    const teamId = parseIdOrSendError(res, teamIdText)
    if (teamId === null) return
    return handleGetTeam(req, res, loginMember, teamId)
  }

  const membersTeamIdText = matchId(/^\/teams\/([^/]+)\/members$/, req.path)
  if (req.method === 'GET' && membersTeamIdText !== null) {
    const teamId = parseIdOrSendError(res, membersTeamIdText)
    if (teamId === null) return
    return handleGetTeamMembers(req, res, loginMember, teamId)
  }

  const inviteCodeTeamIdText = matchId(/^\/teams\/([^/]+)\/invite-code$/, req.path)
  if (inviteCodeTeamIdText !== null && (req.method === 'GET' || req.method === 'POST')) {
    const teamId = parseIdOrSendError(res, inviteCodeTeamIdText)
    if (teamId === null) return
    if (req.method === 'GET') return handleGetInviteCode(req, res, loginMember, teamId)
    return handleIssueInviteCode(req, res, loginMember, teamId)
  }

  const roleMatch = req.path.match(/^\/teams\/([^/]+)\/members\/([^/]+)\/role$/)
  if (req.method === 'POST' && roleMatch) {
    const teamId = parseIdOrSendError(res, roleMatch[1])
    if (teamId === null) return
    const teamMemberId = parseIdOrSendError(res, roleMatch[2])
    if (teamMemberId === null) return
    return handleAssignRole(req, res, loginMember, teamId, teamMemberId)
  }

  // 실제 API에 없는 주소입니다. json-server의 기본 주소(/members 등)로 넘기면 비밀번호까지 그대로 보이므로 막습니다.
  return sendError(res, 404, 'NOT_FOUND', '없는 주소입니다')
}
