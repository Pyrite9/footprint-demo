// 가짜 API(json-server)에 실제 백엔드와 같은 규칙을 붙입니다. 주소마다 handle 함수 하나가 실제 API 하나를 흉내 냅니다.
// json-server 0.17.4가 이 파일을 require로 읽기 때문에 이 파일만 CommonJS(require, module.exports)로 씁니다.
// token은 "mock-token-<회원 id>" 문자열입니다. 실제 JWT와 달리 서명이 없으므로 화면은 token을 풀어 읽지 않습니다.
// 데이터는 db.json에 있습니다. req.app.db는 json-server가 db.json을 읽어 둔 lowdb이고, .write()를 부르면 파일에 저장됩니다.

// crypto, fs, path는 Node.js에 들어 있는 모듈입니다. 초대 코드(UUID)를 만들고, 산출물 파일을 mock/files에 읽고 씁니다.
const crypto = require('crypto')
const fs = require('fs')
const path = require('path')

// 가짜 API의 산출물 파일 본문을 두는 폴더입니다. 실제 백엔드의 MinIO 역할입니다. seed-*는 시작 파일입니다.
const FILES_DIR = path.join(__dirname, 'files')
// 실제 백엔드의 spring.servlet.multipart.max-request-size와 같은 20MB입니다.
const MAX_UPLOAD_BYTES = 20 * 1024 * 1024

const BEARER_TOKEN_PREFIX = 'Bearer mock-token-'
const NOT_VALID_MESSAGE = '입력값이 올바르지 않습니다'
const NOT_READABLE_MESSAGE = '요청 body의 형식이 올바르지 않습니다'
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

// "2026-11-02" 모양이고 달력에 있는 날짜이면 true입니다. 실제 백엔드는 2026-13-01 같은 날짜를 JSON을 읽는 단계에서 거절합니다.
function isValidDate(text) {
  if (typeof text !== 'string' || !DATE_PATTERN.test(text)) return false
  // 13월처럼 아예 없는 날짜는 Invalid Date가 되고, 2월 30일처럼 넘치는 날짜는 다음 달로 넘어갑니다.
  // 그래서 숫자로 바뀌었는지 보고, 다시 글자로 바꿔 처음과 같은지 봅니다.
  const date = new Date(text + 'T00:00:00Z')
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(text)
}

// 실제 백엔드의 ApiErrorResponse와 같은 모양 { code, message }로 오류를 보냅니다.
function sendError(res, status, code, message) {
  res.status(status).json({ code, message })
}

// 값이 없거나 공백뿐이면 true입니다. 백엔드의 @NotBlank와 같은 기준입니다.
function isBlank(value) {
  return typeof value !== 'string' || value.trim() === ''
}

// 서버의 지금 시각을 "2026-11-05T14:03:22" 모양(초까지, 시간대 표시 없음)으로 만듭니다. 실제 백엔드의 LocalDateTime 응답과 같은 모양입니다.
function nowLocalDateTime() {
  const now = new Date()
  const pad = (number) => String(number).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

// db.json의 한 목록에서 다음 id를 정합니다. 실제 DB의 자동 증가 번호를 흉내 냅니다.
function nextId(db, collection) {
  const items = db.get(collection).value()
  return items.length === 0 ? 1 : Math.max(...items.map((item) => item.id)) + 1
}

// Authorization header의 token으로 회원을 찾습니다. token이 없거나 틀리면 undefined입니다.
// 실제 백엔드와 다른 점 하나: 없는 회원 번호의 token도 여기서 401이 됩니다(실제는 GET /members/me가 404).
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

// 실제 백엔드의 google.allowed-domains, google.professor-emails와 같은 값입니다. 환경 변수로 바꿀 수 있습니다.
const GOOGLE_ALLOWED_DOMAINS = (process.env.GOOGLE_ALLOWED_DOMAINS ?? 'g.yju.ac.kr').split(',').map((text) => text.trim())
const PROFESSOR_EMAILS = (process.env.PROFESSOR_EMAILS ?? '').split(',').map((text) => text.trim().toLowerCase()).filter((text) => text !== '')

// 구글 ID token의 가운데 부분(payload)을 풀어 읽습니다. 모양이 깨졌으면 undefined입니다.
// 실제 백엔드와 다른 점: 가짜 API는 구글의 서명을 확인하지 못합니다. 화면 개발용이라 payload만 믿습니다.
function readGooglePayload(credential) {
  try {
    return JSON.parse(Buffer.from(credential.split('.')[1], 'base64url').toString('utf8'))
  } catch {
    return undefined
  }
}

// POST /auth/google: 구글 ID token으로 로그인합니다. 처음 온 계정이면 회원으로 저장합니다.
function handleGoogleLogin(req, res) {
  const { credential } = req.body
  if (isBlank(credential)) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }

  const payload = readGooglePayload(credential)
  if (!payload || payload.email_verified !== true || isBlank(payload.email)) {
    return sendError(res, 401, 'UNAUTHORIZED', '구글 로그인을 확인하지 못했습니다')
  }
  if (!GOOGLE_ALLOWED_DOMAINS.includes(payload.hd)) {
    return sendError(res, 403, 'FORBIDDEN', '학교 계정(' + GOOGLE_ALLOWED_DOMAINS.join(', ') + ')으로만 로그인할 수 있습니다')
  }

  const db = req.app.db
  let member = db.get('members').find({ email: payload.email }).value()
  if (!member) {
    member = {
      id: nextId(db, 'members'),
      email: payload.email,
      // 구글로 만든 회원은 비밀번호가 없습니다. 실제 백엔드의 null과 같습니다.
      password: null,
      name: payload.name ?? payload.email,
      memberType: PROFESSOR_EMAILS.includes(payload.email.toLowerCase()) ? 'PROFESSOR' : 'STUDENT',
    }
    db.get('members').push(member).write()
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
  const { title, leaderEmails } = req.body
  // 실제 백엔드(Jackson)는 빈 글자 ""로 온 날짜를 null로 읽습니다. 그래서 아래 2번 검사(@NotNull)에 걸립니다.
  const startDate = req.body.startDate === '' ? null : req.body.startDate
  const endDate = req.body.endDate === '' ? null : req.body.endDate

  // 1. 날짜가 틀렸거나 leaderEmails가 목록이 아니면 실제 백엔드는 JSON을 읽는 단계에서 거절합니다.
  if ((startDate != null && !isValidDate(startDate)) || (endDate != null && !isValidDate(endDate))
    || (leaderEmails != null && !Array.isArray(leaderEmails))) {
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

  const assignment = { id: nextId(db, 'assignments'), title, startDate, endDate, professorId: loginMember.id, phase: 'RECORDING', evaluationStartedAt: null }
  db.get('assignments').push(assignment).write()
  leaders.forEach((leader, index) => {
    const team = { id: nextId(db, 'teams'), assignmentId: assignment.id, name: index + 1 + '조', inviteCode: null }
    db.get('teams').push(team).write()
    db.get('teamMembers')
      .push({ id: nextId(db, 'teamMembers'), teamId: team.id, memberId: leader.id, leader: true, roleName: null, roleAssignedAt: null })
      .write()
  })

  return res.json(toAssignmentResponse(assignment))
}

// 과제 목록과 상세에 쓰는 과제 모양 { id, title, startDate, endDate, phase, evaluationStartedAt }입니다. 실제 AssignmentResponse와 같습니다.
function toAssignmentResponse(assignment) {
  const { id, title, startDate, endDate, phase, evaluationStartedAt } = assignment
  return { id, title, startDate, endDate, phase, evaluationStartedAt }
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
    const myTeamMember = checkAssignmentMember(db, res, assignment, loginMember)
    if (!myTeamMember) return
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

// POST /assignments/{assignmentId}/start-evaluation: 과제를 만든 교수가 과제를 평가 단계로 바꿉니다.
function handleStartEvaluation(req, res, loginMember, assignmentId) {
  const db = req.app.db
  const assignment = db.get('assignments').find({ id: assignmentId }).value()
  if (!assignment) {
    return sendError(res, 404, 'NOT_FOUND', '과제가 없습니다. id=' + assignmentId)
  }
  if (assignment.professorId !== loginMember.id) {
    return sendError(res, 403, 'FORBIDDEN', '과제를 만든 교수만 평가를 시작할 수 있습니다')
  }
  if (assignment.phase === 'EVALUATION') {
    return sendError(res, 400, 'INVALID_INPUT', '이미 평가 단계입니다')
  }
  db.get('assignments').find({ id: assignmentId }).assign({ phase: 'EVALUATION', evaluationStartedAt: nowLocalDateTime() }).write()
  notifyEvaluationStarted(db, assignment)
  return res.json(toAssignmentResponse(db.get('assignments').find({ id: assignmentId }).value()))
}

// 조원 목록의 조원 한 명 모양 { id, memberId, name, roleName, leader }입니다. 실제 TeamMemberResponse와 같습니다.
function toTeamMemberResponse(db, teamMember) {
  const member = db.get('members').find({ id: teamMember.memberId }).value()
  return { id: teamMember.id, memberId: member.id, name: member.name, roleName: teamMember.roleName, leader: teamMember.leader }
}

// 조 정보 모양 { id, name, assignmentId, assignmentTitle, phase, startDate, endDate }입니다. 실제 TeamResponse와 같습니다.
function toTeamResponse(db, team) {
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  return { id: team.id, name: team.name, assignmentId: assignment.id, assignmentTitle: assignment.title, phase: assignment.phase, startDate: assignment.startDate, endDate: assignment.endDate }
}

// 아래 check 함수들은 실제 TeamAccessChecker와 같은 이름, 같은 규칙입니다.
// 실패하면 403을 보내고 false나 undefined를 돌려줍니다. 부른 쪽은 if (!결과) return 으로 바로 끝냅니다.

// 실제 TeamAccessChecker.checkAssignmentMember와 같습니다. 이 과제의 조원이면 조 참여 기록, 아니면 403을 보내고 undefined입니다.
function checkAssignmentMember(db, res, assignment, loginMember) {
  const teamIds = db.get('teams').filter({ assignmentId: assignment.id }).map('id').value()
  const teamMember = db.get('teamMembers').find((item) => item.memberId === loginMember.id && teamIds.includes(item.teamId)).value()
  if (!teamMember) sendError(res, 403, 'FORBIDDEN', '이 과제에 접근할 권한이 없습니다')
  return teamMember
}

// 실제 TeamAccessChecker.checkTeamAccess와 같습니다. 그 조의 조원이거나 그 과제를 만든 교수이면 true, 아니면 403을 보내고 false입니다.
function checkTeamAccess(db, res, team, loginMember) {
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  if (assignment.professorId === loginMember.id) return true
  return checkTeamMember(db, res, team, loginMember) !== undefined
}

// 실제 TeamAccessChecker.checkTeamMember와 같습니다. 조원이면 조 참여 기록, 아니면 403을 보내고 undefined입니다.
function checkTeamMember(db, res, team, loginMember) {
  const teamMember = db.get('teamMembers').find({ teamId: team.id, memberId: loginMember.id }).value()
  if (!teamMember) sendError(res, 403, 'FORBIDDEN', '이 조에 접근할 권한이 없습니다')
  return teamMember
}

// 실제 TeamAccessChecker.checkLeader와 같습니다. 조장이면 조 참여 기록, 아니면 403을 보내고 undefined입니다.
function checkLeader(db, res, team, loginMember) {
  const teamMember = db.get('teamMembers').find({ teamId: team.id, memberId: loginMember.id }).value()
  if (!teamMember || !teamMember.leader) {
    sendError(res, 403, 'FORBIDDEN', '조장만 할 수 있습니다')
    return undefined
  }
  return teamMember
}

// 실제 AssignmentPhaseChecker.checkRecording과 같습니다. 기록 단계이면 true, 아니면 400을 보내고 false입니다.
function checkRecording(db, res, team) {
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  if (assignment.phase === 'RECORDING') return true
  sendError(res, 400, 'INVALID_INPUT', '평가 단계에서는 조 운영과 과정 기록을 바꿀 수 없습니다')
  return false
}

// 실제 AssignmentPhaseChecker.checkEvaluation과 같습니다. 평가 단계이면 true, 아니면 400을 보내고 false입니다.
function checkEvaluation(db, res, team) {
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  if (assignment.phase === 'EVALUATION') return true
  sendError(res, 400, 'INVALID_INPUT', '동료평가는 평가 단계에서만 입력할 수 있습니다')
  return false
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
  if (!team || !checkLeader(db, res, team, loginMember) || !checkRecording(db, res, team)) return
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
  if (!checkRecording(db, res, team)) return

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
  if (!team || !checkLeader(db, res, team, loginMember) || !checkRecording(db, res, team)) return

  const target = db.get('teamMembers').find({ id: teamMemberId, teamId }).value()
  if (!target) {
    return sendError(res, 404, 'NOT_FOUND', '이 조의 조원이 아닙니다. id=' + teamMemberId)
  }
  if (target.roleName !== null) {
    return sendError(res, 409, 'DUPLICATE', '이미 역할이 지정된 조원입니다')
  }

  // 실제 백엔드처럼 지정 시각은 서버(가짜 API)의 현재 시각입니다.
  db.get('teamMembers').find({ id: teamMemberId }).assign({ roleName: roleName.trim(), roleAssignedAt: nowLocalDateTime() }).write()
  const updated = db.get('teamMembers').find({ id: teamMemberId }).value()
  return res.json(toTeamMemberResponse(db, updated))
}

// PUT /teams/{teamId}/members/{teamMemberId}/role: 조장이 조원의 역할을 바꾸고 이전 역할을 이력으로 남깁니다. 검사 순서는 실제 백엔드와 같습니다.
function handleChangeRole(req, res, loginMember, teamId, teamMemberId) {
  const db = req.app.db
  const { roleName } = req.body
  // @NotBlank @Size(max = 255) 검사입니다. 실제 백엔드처럼 권한 검사보다 먼저입니다.
  if (isBlank(roleName) || roleName.length > 255) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkLeader(db, res, team, loginMember) || !checkRecording(db, res, team)) return

  const target = db.get('teamMembers').find({ id: teamMemberId, teamId }).value()
  if (!target) {
    return sendError(res, 404, 'NOT_FOUND', '이 조의 조원이 아닙니다. id=' + teamMemberId)
  }
  if (target.roleName === null) {
    return sendError(res, 400, 'INVALID_INPUT', '아직 역할이 지정되지 않은 조원입니다')
  }
  const newRoleName = roleName.trim()
  if (newRoleName === target.roleName) {
    return sendError(res, 400, 'INVALID_INPUT', '지금과 같은 역할입니다')
  }

  const now = nowLocalDateTime()
  db.get('roleHistories')
    .push({ id: nextId(db, 'roleHistories'), teamMemberId, previousRoleName: target.roleName, previousRoleAssignedAt: target.roleAssignedAt, changedAt: now })
    .write()
  db.get('teamMembers').find({ id: teamMemberId }).assign({ roleName: newRoleName, roleAssignedAt: now }).write()
  const updated = db.get('teamMembers').find({ id: teamMemberId }).value()
  return res.json(toTeamMemberResponse(db, updated))
}

// GET /teams/{teamId}/members/{teamMemberId}/role-histories: 한 조원의 역할 이력을 최근에 바뀐 것부터 돌려줍니다.
function handleGetRoleHistories(req, res, loginMember, teamId, teamMemberId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkTeamAccess(db, res, team, loginMember)) return

  const target = db.get('teamMembers').find({ id: teamMemberId, teamId }).value()
  if (!target) {
    return sendError(res, 404, 'NOT_FOUND', '이 조의 조원이 아닙니다. id=' + teamMemberId)
  }
  const histories = db.get('roleHistories').filter({ teamMemberId }).sortBy('id').reverse().value()
  return res.json(histories.map(({ id, previousRoleName, changedAt }) => ({ id, previousRoleName, changedAt })))
}

// PUT /teams/{teamId}/leader: 과제를 만든 교수가 조장을 바꾸고 이력을 남깁니다. 검사 순서는 실제 백엔드와 같습니다.
function handleChangeLeader(req, res, loginMember, teamId) {
  const db = req.app.db
  const { teamMemberId } = req.body
  // 숫자로 읽을 수 없으면 실제 백엔드는 JSON을 읽는 단계에서, 빠졌으면 @NotNull 검사에서 400입니다. 둘 다 권한 검사보다 먼저입니다.
  if (teamMemberId != null && !Number.isInteger(teamMemberId)) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_READABLE_MESSAGE)
  }
  if (teamMemberId == null) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  const team = findTeamOrSendError(db, res, teamId)
  if (!team) return
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  if (assignment.professorId !== loginMember.id) {
    return sendError(res, 403, 'FORBIDDEN', '과제를 만든 교수만 조장을 바꿀 수 있습니다')
  }
  if (!checkRecording(db, res, team)) return

  const newLeader = db.get('teamMembers').find({ id: teamMemberId, teamId }).value()
  if (!newLeader) {
    return sendError(res, 404, 'NOT_FOUND', '이 조의 조원이 아닙니다. id=' + teamMemberId)
  }
  if (newLeader.leader) {
    return sendError(res, 400, 'INVALID_INPUT', '이미 이 조의 조장입니다')
  }

  const previousLeader = db.get('teamMembers').find({ teamId, leader: true }).value()
  db.get('leaderHistories')
    .push({ id: nextId(db, 'leaderHistories'), teamId, previousLeaderId: previousLeader.id, newLeaderId: newLeader.id, changedAt: nowLocalDateTime() })
    .write()
  db.get('teamMembers').find({ id: previousLeader.id }).assign({ leader: false }).write()
  db.get('teamMembers').find({ id: newLeader.id }).assign({ leader: true }).write()
  return res.json(toTeamMemberResponse(db, db.get('teamMembers').find({ id: newLeader.id }).value()))
}

// 조 참여 번호로 회원 이름을 찾습니다. 조장 교체 이력을 이름으로 펼칠 때 씁니다.
function findTeamMemberName(db, teamMemberId) {
  const teamMember = db.get('teamMembers').find({ id: teamMemberId }).value()
  return db.get('members').find({ id: teamMember.memberId }).value().name
}

// GET /teams/{teamId}/leader-histories: 한 조의 조장 교체 이력을 최근에 바뀐 것부터 돌려줍니다. 실제 LeaderHistoryResponse와 같은 모양입니다.
function handleGetLeaderHistories(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkTeamAccess(db, res, team, loginMember)) return
  const histories = db.get('leaderHistories').filter({ teamId }).sortBy('id').reverse().value()
  return res.json(histories.map(({ id, previousLeaderId, newLeaderId, changedAt }) => ({
    id,
    previousLeaderName: findTeamMemberName(db, previousLeaderId),
    newLeaderName: findTeamMemberName(db, newLeaderId),
    changedAt,
  })))
}

// 산출물 한 건의 모양 { id, roleName, fileName, fileSize, uploaderMemberId, uploaderName, uploadedAt, deletedAt }입니다. 실제 DeliverableResponse와 같습니다.
function toDeliverableResponse(db, deliverable) {
  const teamMember = db.get('teamMembers').find({ id: deliverable.teamMemberId }).value()
  const member = db.get('members').find({ id: teamMember.memberId }).value()
  const { id, roleName, fileName, fileSize, uploadedAt, deletedAt } = deliverable
  return { id, roleName, fileName, fileSize, uploaderMemberId: member.id, uploaderName: member.name, uploadedAt, deletedAt }
}

// 요청 body를 끝까지 읽어 Buffer 하나로 돌려줍니다. 20MB를 넘으면 'TOO_LARGE'를 돌려줍니다.
// json-server는 JSON body만 읽어 주므로 multipart body는 여기서 직접 읽습니다.
function readRawBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = []
    let size = 0
    let tooLarge = false
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > MAX_UPLOAD_BYTES) tooLarge = true
      if (!tooLarge) chunks.push(chunk)
    })
    req.on('end', () => resolve(tooLarge ? 'TOO_LARGE' : Buffer.concat(chunks)))
    req.on('error', reject)
  })
}

// multipart/form-data body에서 이름이 file인 칸을 찾아 { fileName, content }로 돌려줍니다. 없으면 null입니다.
// 실제 백엔드에서는 Spring이 해 주는 일입니다. 가짜 API에 라이브러리를 더하지 않으려고 짧게 직접 씁니다.
function findFilePart(body, boundary) {
  // body는 "--boundary\r\n(header)\r\n\r\n(내용)\r\n--boundary..." 모양입니다.
  const delimiter = Buffer.from('--' + boundary)
  let start = body.indexOf(delimiter)
  while (start !== -1) {
    const next = body.indexOf(delimiter, start + delimiter.length)
    if (next === -1) break
    const part = body.subarray(start + delimiter.length + 2, next - 2)
    const headerEnd = part.indexOf('\r\n\r\n')
    // header는 글자 하나가 1byte인 latin1로 읽고, 파일 이름만 UTF-8로 다시 읽습니다(브라우저는 한글 이름을 UTF-8로 보냅니다).
    const header = part.subarray(0, headerEnd).toString('latin1')
    const fileNameMatch = header.match(/filename="([^"]*)"/)
    if (/name="file"/.test(header) && fileNameMatch) {
      const fileName = Buffer.from(fileNameMatch[1], 'latin1').toString('utf8')
      return { fileName, content: part.subarray(headerEnd + 4) }
    }
    start = next
  }
  return null
}

// GET /teams/{teamId}/deliverables: 조의 산출물을 최신순으로 돌려줍니다.
function handleGetDeliverables(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkTeamAccess(db, res, team, loginMember)) return
  const teamMemberIds = db.get('teamMembers').filter({ teamId }).map('id').value()
  const deliverables = db.get('deliverables')
    .filter((deliverable) => teamMemberIds.includes(deliverable.teamMemberId))
    .sortBy('id').reverse().value()
  return res.json(deliverables.map((deliverable) => toDeliverableResponse(db, deliverable)))
}

// POST /teams/{teamId}/deliverables: 역할이 있는 조원이 파일 하나를 올립니다. 검사 순서는 실제 백엔드와 같습니다.
// next: 파일을 읽는 도중(.then 안)에 난 오류는 Express가 스스로 잡지 못해서, next로 아래 handleError에 넘깁니다.
function handleUploadDeliverable(req, res, next, loginMember, teamId) {
  const db = req.app.db
  const boundaryMatch = (req.headers['content-type'] || '').match(/^multipart\/form-data;.*boundary="?([^";]+)"?/)
  if (!boundaryMatch) {
    return sendError(res, 400, 'INVALID_INPUT', '파일 업로드 요청의 형식이 올바르지 않습니다')
  }

  readRawBody(req).then((body) => {
    if (body === 'TOO_LARGE') {
      return sendError(res, 400, 'INVALID_INPUT', '파일은 20MB까지 올릴 수 있습니다')
    }
    const filePart = findFilePart(body, boundaryMatch[1])
    if (!filePart) {
      return sendError(res, 400, 'INVALID_INPUT', '파일이 없습니다')
    }
    if (filePart.content.length === 0) {
      return sendError(res, 400, 'INVALID_INPUT', '빈 파일은 올릴 수 없습니다')
    }
    const team = findTeamOrSendError(db, res, teamId)
    if (!team) return
    const uploader = checkTeamMember(db, res, team, loginMember)
    if (!uploader) return
    if (uploader.roleName === null) {
      return sendError(res, 403, 'FORBIDDEN', '역할이 배정된 조원만 올릴 수 있습니다')
    }
    if (!checkRecording(db, res, team)) return

    // 실제 백엔드처럼 파일 본문을 먼저 저장하고(MinIO 대신 mock/files), 그다음 db.json에 저장합니다.
    const storageKey = crypto.randomUUID()
    fs.writeFileSync(path.join(FILES_DIR, storageKey), filePart.content)
    const deliverable = {
      id: nextId(db, 'deliverables'),
      teamMemberId: uploader.id,
      roleName: uploader.roleName,
      fileName: filePart.fileName === '' ? 'file' : filePart.fileName,
      fileSize: filePart.content.length,
      storageKey,
      uploadedAt: nowLocalDateTime(),
      deletedAt: null,
    }
    db.get('deliverables').push(deliverable).write()
    return res.json(toDeliverableResponse(db, deliverable))
  }).catch(next)
}

// GET /deliverables/{deliverableId}/download: 파일 본문을 내려줍니다.
function handleDownloadDeliverable(req, res, loginMember, deliverableId) {
  const db = req.app.db
  const deliverable = db.get('deliverables').find({ id: deliverableId }).value()
  if (!deliverable) {
    return sendError(res, 404, 'NOT_FOUND', '산출물이 없습니다. id=' + deliverableId)
  }
  const teamMember = db.get('teamMembers').find({ id: deliverable.teamMemberId }).value()
  const team = db.get('teams').find({ id: teamMember.teamId }).value()
  if (!checkTeamAccess(db, res, team, loginMember)) return
  if (deliverable.deletedAt !== null) {
    return sendError(res, 404, 'NOT_FOUND', '삭제된 산출물입니다. id=' + deliverableId)
  }

  const filePath = path.join(FILES_DIR, deliverable.storageKey)
  if (!fs.existsSync(filePath)) {
    return sendError(res, 404, 'NOT_FOUND', '파일이 없습니다. key=' + deliverable.storageKey)
  }
  // res.attachment는 Content-Disposition(한글 이름은 filename*=UTF-8''...)과 확장자에 맞는 Content-Type을 붙입니다.
  res.attachment(deliverable.fileName)
  return res.send(fs.readFileSync(filePath))
}

// DELETE /deliverables/{deliverableId}: 올린 조원이 산출물을 삭제합니다. 파일 본문만 지우고 db.json의 행은 삭제 시각과 함께 남깁니다.
function handleDeleteDeliverable(req, res, loginMember, deliverableId) {
  const db = req.app.db
  const deliverable = db.get('deliverables').find({ id: deliverableId }).value()
  if (!deliverable) {
    return sendError(res, 404, 'NOT_FOUND', '산출물이 없습니다. id=' + deliverableId)
  }
  const teamMember = db.get('teamMembers').find({ id: deliverable.teamMemberId }).value()
  if (teamMember.memberId !== loginMember.id) {
    return sendError(res, 403, 'FORBIDDEN', '올린 조원만 삭제할 수 있습니다')
  }
  const team = db.get('teams').find({ id: teamMember.teamId }).value()
  if (!checkRecording(db, res, team)) return
  if (deliverable.deletedAt !== null) {
    return sendError(res, 400, 'INVALID_INPUT', '이미 삭제된 산출물입니다')
  }

  db.get('deliverables').find({ id: deliverableId }).assign({ deletedAt: nowLocalDateTime() }).write()
  // force: 파일이 이미 없어도 오류를 내지 않습니다. 실제 저장소(S3)도 없는 key를 지울 때 오류를 내지 않습니다.
  fs.rmSync(path.join(FILES_DIR, deliverable.storageKey), { force: true })
  return res.json(toDeliverableResponse(db, db.get('deliverables').find({ id: deliverableId }).value()))
}

// 기여 항목 한 건의 모양입니다. 실제 ContributionResponse와 같습니다.
function toContributionResponse(db, contribution) {
  const { id, title, assigneeId, createdAt, reportMemo, reportedAt, canceledAt } = contribution
  const teamMember = db.get('teamMembers').find({ id: assigneeId }).value()
  const member = db.get('members').find({ id: teamMember.memberId }).value()
  return { id, title, assigneeMemberId: member.id, assigneeName: member.name, createdAt, reportMemo, reportedAt, canceledAt }
}

// POST /teams/{teamId}/contributions: 조장이 항목명과 담당 조원을 정해 기여 항목을 등록합니다.
function handleCreateContribution(req, res, loginMember, teamId) {
  const db = req.app.db
  const { title, teamMemberId } = req.body
  // 숫자로 읽을 수 없으면 실제 백엔드는 JSON을 읽는 단계에서, 나머지는 @NotBlank @Size @NotNull 검사에서 400입니다. 모두 권한 검사보다 먼저입니다.
  if (teamMemberId != null && !Number.isInteger(teamMemberId)) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_READABLE_MESSAGE)
  }
  if (isBlank(title) || title.length > 255 || teamMemberId == null) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkLeader(db, res, team, loginMember) || !checkRecording(db, res, team)) return

  const assignee = db.get('teamMembers').find({ id: teamMemberId, teamId }).value()
  if (!assignee) {
    return sendError(res, 404, 'NOT_FOUND', '이 조의 조원이 아닙니다. id=' + teamMemberId)
  }

  const contribution = {
    id: nextId(db, 'contributions'),
    teamId,
    assigneeId: assignee.id,
    title: title.trim(),
    createdAt: nowLocalDateTime(),
    reportMemo: null,
    reportedAt: null,
    canceledAt: null,
  }
  db.get('contributions').push(contribution).write()
  notifyContributionAssigned(db, contribution, loginMember.id)
  return res.json(toContributionResponse(db, contribution))
}

// GET /teams/{teamId}/contributions: 조의 기여 항목을 최근에 등록한 것부터 돌려줍니다. 취소된 항목도 빼지 않습니다.
function handleGetContributions(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkTeamAccess(db, res, team, loginMember)) return
  const contributions = db.get('contributions').filter({ teamId }).sortBy('id').reverse().value()
  return res.json(contributions.map((contribution) => toContributionResponse(db, contribution)))
}

// POST /contributions/{contributionId}/report: 담당 조원이 완료 보고합니다. 한 번만 할 수 있습니다.
function handleReportContribution(req, res, loginMember, contributionId) {
  const db = req.app.db
  const { memo } = req.body
  // @NotBlank @Size(max = 2000) 검사입니다. 실제 백엔드처럼 권한 검사보다 먼저입니다.
  if (isBlank(memo) || memo.length > 2000) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  const contribution = db.get('contributions').find({ id: contributionId }).value()
  if (!contribution) {
    return sendError(res, 404, 'NOT_FOUND', '기여 항목이 없습니다. id=' + contributionId)
  }
  const assignee = db.get('teamMembers').find({ id: contribution.assigneeId }).value()
  if (assignee.memberId !== loginMember.id) {
    return sendError(res, 403, 'FORBIDDEN', '담당 조원만 완료 보고할 수 있습니다')
  }
  const team = db.get('teams').find({ id: contribution.teamId }).value()
  if (!checkRecording(db, res, team)) return
  if (contribution.canceledAt !== null) {
    return sendError(res, 400, 'INVALID_INPUT', '취소된 항목은 보고할 수 없습니다')
  }
  if (contribution.reportedAt !== null) {
    return sendError(res, 409, 'DUPLICATE', '이미 완료 보고한 항목입니다')
  }

  db.get('contributions').find({ id: contributionId }).assign({ reportMemo: memo.trim(), reportedAt: nowLocalDateTime() }).write()
  notifyContributionReported(db, contribution)
  return res.json(toContributionResponse(db, db.get('contributions').find({ id: contributionId }).value()))
}

// POST /contributions/{contributionId}/cancel: 조장이 보고 전 항목을 취소합니다. 행은 취소 시각과 함께 남습니다.
function handleCancelContribution(req, res, loginMember, contributionId) {
  const db = req.app.db
  const contribution = db.get('contributions').find({ id: contributionId }).value()
  if (!contribution) {
    return sendError(res, 404, 'NOT_FOUND', '기여 항목이 없습니다. id=' + contributionId)
  }
  const team = db.get('teams').find({ id: contribution.teamId }).value()
  if (!checkLeader(db, res, team, loginMember) || !checkRecording(db, res, team)) return
  if (contribution.canceledAt !== null) {
    return sendError(res, 400, 'INVALID_INPUT', '이미 취소된 항목입니다')
  }
  if (contribution.reportedAt !== null) {
    return sendError(res, 400, 'INVALID_INPUT', '보고한 항목은 취소할 수 없습니다')
  }

  db.get('contributions').find({ id: contributionId }).assign({ canceledAt: nowLocalDateTime() }).write()
  return res.json(toContributionResponse(db, db.get('contributions').find({ id: contributionId }).value()))
}

// 완료확인 한 건의 모양입니다. 실제 ConfirmationResponse와 같습니다.
function toConfirmationResponse(db, confirmation) {
  const { id, deliverableId, contributionId, confirmerId, confirmedAt, canceledAt } = confirmation
  const teamMember = db.get('teamMembers').find({ id: confirmerId }).value()
  const member = db.get('members').find({ id: teamMember.memberId }).value()
  return { id, deliverableId, contributionId, confirmerMemberId: member.id, confirmerName: member.name, confirmedAt, canceledAt }
}

// 완료확인 행을 저장하고 응답을 보냅니다. target은 { deliverableId } 또는 { contributionId }입니다.
function saveConfirmation(db, res, confirmer, target) {
  const confirmation = {
    id: nextId(db, 'confirmations'),
    confirmerId: confirmer.id,
    deliverableId: null,
    contributionId: null,
    ...target,
    confirmedAt: nowLocalDateTime(),
    canceledAt: null,
  }
  db.get('confirmations').push(confirmation).write()
  return res.json(toConfirmationResponse(db, confirmation))
}

// 이 조원의 유효한(취소하지 않은) 확인이 이 대상에 있으면 true입니다.
function hasActiveConfirmation(db, confirmer, target) {
  return db.get('confirmations').find({ confirmerId: confirmer.id, canceledAt: null, ...target }).value() !== undefined
}

// POST /deliverables/{deliverableId}/confirmations: 올린 조원을 뺀 같은 조 조원이 산출물을 확인합니다.
function handleConfirmDeliverable(req, res, loginMember, deliverableId) {
  const db = req.app.db
  const deliverable = db.get('deliverables').find({ id: deliverableId }).value()
  if (!deliverable) {
    return sendError(res, 404, 'NOT_FOUND', '산출물이 없습니다. id=' + deliverableId)
  }
  const uploader = db.get('teamMembers').find({ id: deliverable.teamMemberId }).value()
  const team = db.get('teams').find({ id: uploader.teamId }).value()
  const confirmer = checkTeamMember(db, res, team, loginMember)
  if (!confirmer) return
  if (uploader.id === confirmer.id) {
    return sendError(res, 403, 'FORBIDDEN', '자기가 올린 산출물은 확인할 수 없습니다')
  }
  if (!checkRecording(db, res, team)) return
  if (deliverable.deletedAt !== null) {
    return sendError(res, 400, 'INVALID_INPUT', '삭제된 산출물은 확인할 수 없습니다')
  }
  if (hasActiveConfirmation(db, confirmer, { deliverableId })) {
    return sendError(res, 409, 'DUPLICATE', '이미 확인한 기록입니다')
  }
  return saveConfirmation(db, res, confirmer, { deliverableId })
}

// POST /contributions/{contributionId}/confirmations: 담당 조원을 뺀 같은 조 조원이 보고된 기여 항목을 확인합니다.
function handleConfirmContribution(req, res, loginMember, contributionId) {
  const db = req.app.db
  const contribution = db.get('contributions').find({ id: contributionId }).value()
  if (!contribution) {
    return sendError(res, 404, 'NOT_FOUND', '기여 항목이 없습니다. id=' + contributionId)
  }
  const team = db.get('teams').find({ id: contribution.teamId }).value()
  const confirmer = checkTeamMember(db, res, team, loginMember)
  if (!confirmer) return
  if (contribution.assigneeId === confirmer.id) {
    return sendError(res, 403, 'FORBIDDEN', '자기가 맡은 항목은 확인할 수 없습니다')
  }
  if (!checkRecording(db, res, team)) return
  if (contribution.canceledAt !== null) {
    return sendError(res, 400, 'INVALID_INPUT', '취소된 항목은 확인할 수 없습니다')
  }
  if (contribution.reportedAt === null) {
    return sendError(res, 400, 'INVALID_INPUT', '보고 전 항목은 확인할 수 없습니다')
  }
  if (hasActiveConfirmation(db, confirmer, { contributionId })) {
    return sendError(res, 409, 'DUPLICATE', '이미 확인한 기록입니다')
  }
  return saveConfirmation(db, res, confirmer, { contributionId })
}

// POST /confirmations/{confirmationId}/cancel: 확인한 조원 본인이 자기 확인을 취소합니다. 행은 취소 시각과 함께 남습니다.
function handleCancelConfirmation(req, res, loginMember, confirmationId) {
  const db = req.app.db
  const confirmation = db.get('confirmations').find({ id: confirmationId }).value()
  if (!confirmation) {
    return sendError(res, 404, 'NOT_FOUND', '완료확인이 없습니다. id=' + confirmationId)
  }
  const confirmer = db.get('teamMembers').find({ id: confirmation.confirmerId }).value()
  if (confirmer.memberId !== loginMember.id) {
    return sendError(res, 403, 'FORBIDDEN', '확인한 조원만 취소할 수 있습니다')
  }
  const team = db.get('teams').find({ id: confirmer.teamId }).value()
  if (!checkRecording(db, res, team)) return
  if (confirmation.canceledAt !== null) {
    return sendError(res, 400, 'INVALID_INPUT', '이미 취소한 확인입니다')
  }

  db.get('confirmations').find({ id: confirmationId }).assign({ canceledAt: nowLocalDateTime() }).write()
  return res.json(toConfirmationResponse(db, db.get('confirmations').find({ id: confirmationId }).value()))
}

// 대상의 완료확인 이력을 최근 것부터 보냅니다. 취소된 확인도 빼지 않습니다.
function sendConfirmations(db, res, target) {
  const confirmations = db.get('confirmations').filter(target).sortBy('id').reverse().value()
  return res.json(confirmations.map((confirmation) => toConfirmationResponse(db, confirmation)))
}

// GET /deliverables/{deliverableId}/confirmations: 산출물의 완료확인 이력입니다. 조원과 교수가 봅니다.
function handleGetDeliverableConfirmations(req, res, loginMember, deliverableId) {
  const db = req.app.db
  const deliverable = db.get('deliverables').find({ id: deliverableId }).value()
  if (!deliverable) {
    return sendError(res, 404, 'NOT_FOUND', '산출물이 없습니다. id=' + deliverableId)
  }
  const uploader = db.get('teamMembers').find({ id: deliverable.teamMemberId }).value()
  const team = db.get('teams').find({ id: uploader.teamId }).value()
  if (!checkTeamAccess(db, res, team, loginMember)) return
  return sendConfirmations(db, res, { deliverableId })
}

// GET /contributions/{contributionId}/confirmations: 기여 항목의 완료확인 이력입니다. 조원과 교수가 봅니다.
function handleGetContributionConfirmations(req, res, loginMember, contributionId) {
  const db = req.app.db
  const contribution = db.get('contributions').find({ id: contributionId }).value()
  if (!contribution) {
    return sendError(res, 404, 'NOT_FOUND', '기여 항목이 없습니다. id=' + contributionId)
  }
  const team = db.get('teams').find({ id: contribution.teamId }).value()
  if (!checkTeamAccess(db, res, team, loginMember)) return
  return sendConfirmations(db, res, { contributionId })
}

// 동료평가 6개 항목의 이름입니다. 실제 EvaluationSubmitRequest.Item의 점수 필드와 같습니다.
const SCORE_FIELDS = ['goalSharing', 'roleFulfillment', 'encouragement', 'onlineCommunication', 'offlineCommunication', 'progressCheck']

// 내 제출 여부의 모양 { submitted, submittedAt }입니다. 실제 EvaluationStatusResponse와 같습니다. 점수는 담지 않습니다.
function toEvaluationStatusResponse(myEvaluations) {
  if (myEvaluations.length === 0) return { submitted: false, submittedAt: null }
  return { submitted: true, submittedAt: myEvaluations[0].submittedAt }
}

// 점수에 정수가 아닌 값(4.5, "5" 등)이 있으면 true입니다. 실제 백엔드는 이 값을 Integer로 읽지 못해서 다른 문구의 400을 돌려줍니다.
function hasNotIntegerScore(evaluations) {
  return Array.isArray(evaluations) && evaluations.some((item) => item !== null && typeof item === 'object'
    && SCORE_FIELDS.some((field) => item[field] != null && !Number.isInteger(item[field])))
}

// 평가 하나가 실제 백엔드의 @NotNull, @Min(1), @Max(5), @Size(max = 2000) 검사를 통과하면 true입니다.
function isValidEvaluationItem(item) {
  if (item === null || typeof item !== 'object') return false
  if (typeof item.targetTeamMemberId !== 'number') return false
  const scoresValid = SCORE_FIELDS.every((field) => Number.isInteger(item[field]) && item[field] >= 1 && item[field] <= 5)
  const commentValid = item.comment == null || (typeof item.comment === 'string' && item.comment.length <= 2000)
  return scoresValid && commentValid
}

// 임시 저장 행을 실제 EvaluationDraftResponse 모양으로 바꿉니다.
function toEvaluationDraftResponse(draft) {
  const scores = {}
  SCORE_FIELDS.forEach((field) => { scores[field] = draft[field] })
  return { targetTeamMemberId: draft.targetId, ...scores, comment: draft.comment, savedAt: draft.savedAt }
}

// 임시 저장 하나가 실제 백엔드의 검사를 통과하면 true입니다. 제출과 달리 점수는 빠지거나 null이어도 됩니다.
function isValidEvaluationDraftItem(item) {
  if (item === null || typeof item !== 'object') return false
  if (typeof item.targetTeamMemberId !== 'number') return false
  const scoresValid = SCORE_FIELDS.every((field) => item[field] == null || (Number.isInteger(item[field]) && item[field] >= 1 && item[field] <= 5))
  const commentValid = item.comment == null || (typeof item.comment === 'string' && item.comment.length <= 2000)
  return scoresValid && commentValid
}

// GET /teams/{teamId}/evaluations/draft: 내가 이 조에서 임시 저장한 내용을 돌려줍니다. 교수는 조원이 아니라서 403입니다.
function handleGetMyEvaluationDrafts(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team) return
  const evaluator = checkTeamMember(db, res, team, loginMember)
  if (!evaluator) return
  return res.json(db.get('evaluationDrafts').filter({ evaluatorId: evaluator.id }).value().map(toEvaluationDraftResponse))
}

// PUT /teams/{teamId}/evaluations/draft: 이전 임시 저장을 모두 지우고 보낸 내용으로 바꿉니다. 검사 순서는 실제 백엔드와 같습니다.
function handleSaveEvaluationDrafts(req, res, loginMember, teamId) {
  const db = req.app.db
  const { evaluations } = req.body
  if (hasNotIntegerScore(evaluations)) return sendError(res, 400, 'INVALID_INPUT', NOT_READABLE_MESSAGE)
  // 1. @NotNull @Valid 검사입니다. 실제 백엔드처럼 권한 검사보다 먼저입니다. 빈 목록은 통과합니다.
  if (!Array.isArray(evaluations) || !evaluations.every(isValidEvaluationDraftItem)) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  // 2. 여기부터는 Service의 검사입니다.
  const team = findTeamOrSendError(db, res, teamId)
  if (!team) return
  const evaluator = checkTeamMember(db, res, team, loginMember)
  if (!evaluator) return
  if (!checkEvaluation(db, res, team)) return
  if (db.get('evaluations').filter({ evaluatorId: evaluator.id }).value().length > 0) {
    return sendError(res, 409, 'DUPLICATE', '이미 동료평가를 제출했습니다')
  }

  const targetIds = db.get('teamMembers').filter({ teamId }).map('id').value().filter((id) => id !== evaluator.id)
  const savedIds = new Set()
  for (const item of evaluations) {
    if (savedIds.has(item.targetTeamMemberId)) {
      return sendError(res, 400, 'INVALID_INPUT', '같은 조원을 두 번 평가했습니다. id=' + item.targetTeamMemberId)
    }
    savedIds.add(item.targetTeamMemberId)
    if (!targetIds.includes(item.targetTeamMemberId)) {
      return sendError(res, 400, 'INVALID_INPUT', '평가 대상이 아닌 조원입니다. id=' + item.targetTeamMemberId)
    }
  }

  db.get('evaluationDrafts').remove({ evaluatorId: evaluator.id }).write()
  // 실제 백엔드처럼 한 번에 저장한 내용은 모두 같은 시각입니다. 의견은 앞뒤 공백을 지우고, 비었으면 null입니다.
  const savedAt = nowLocalDateTime()
  for (const item of evaluations) {
    const scores = {}
    SCORE_FIELDS.forEach((field) => { scores[field] = item[field] ?? null })
    const comment = isBlank(item.comment) ? null : item.comment.trim()
    db.get('evaluationDrafts')
      .push({ id: nextId(db, 'evaluationDrafts'), evaluatorId: evaluator.id, targetId: item.targetTeamMemberId, ...scores, comment, savedAt })
      .write()
  }
  return res.json(db.get('evaluationDrafts').filter({ evaluatorId: evaluator.id }).value().map(toEvaluationDraftResponse))
}

// GET /teams/{teamId}/evaluations/mine: 내가 이 조에서 동료평가를 제출했는지 돌려줍니다.
function handleGetMyEvaluationStatus(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team) return
  const evaluator = checkTeamMember(db, res, team, loginMember)
  if (!evaluator) return
  const myEvaluations = db.get('evaluations').filter({ evaluatorId: evaluator.id }).value()
  return res.json(toEvaluationStatusResponse(myEvaluations))
}

// POST /teams/{teamId}/evaluations: 자신을 뺀 조원 전원의 평가를 한 번에 저장합니다. 검사 순서는 실제 백엔드와 같습니다.
function handleSubmitEvaluations(req, res, loginMember, teamId) {
  const db = req.app.db
  const { evaluations } = req.body
  if (hasNotIntegerScore(evaluations)) return sendError(res, 400, 'INVALID_INPUT', NOT_READABLE_MESSAGE)
  // 1. @NotEmpty @Valid 검사입니다. 실제 백엔드처럼 권한 검사보다 먼저입니다.
  if (!Array.isArray(evaluations) || evaluations.length === 0 || !evaluations.every(isValidEvaluationItem)) {
    return sendError(res, 400, 'INVALID_INPUT', NOT_VALID_MESSAGE)
  }
  // 2. 여기부터는 Service의 검사입니다.
  const team = findTeamOrSendError(db, res, teamId)
  if (!team) return
  const evaluator = checkTeamMember(db, res, team, loginMember)
  if (!evaluator) return
  if (!checkEvaluation(db, res, team)) return
  if (db.get('evaluations').filter({ evaluatorId: evaluator.id }).value().length > 0) {
    return sendError(res, 409, 'DUPLICATE', '이미 동료평가를 제출했습니다')
  }

  // 평가 대상 = 이 조의 조원 중 나를 뺀 사람입니다.
  const targetIds = db.get('teamMembers').filter({ teamId }).map('id').value().filter((id) => id !== evaluator.id)
  // 조에 나 혼자면 평가할 사람이 없습니다. 실제 백엔드와 같은 문구로 알려 줍니다.
  if (targetIds.length === 0) {
    return sendError(res, 400, 'INVALID_INPUT', '평가할 조원이 없습니다. 조에 자신만 있습니다')
  }
  const submittedIds = new Set()
  for (const item of evaluations) {
    if (submittedIds.has(item.targetTeamMemberId)) {
      return sendError(res, 400, 'INVALID_INPUT', '같은 조원을 두 번 평가했습니다. id=' + item.targetTeamMemberId)
    }
    submittedIds.add(item.targetTeamMemberId)
  }
  // 겹치는 값이 없으므로 개수가 같고 모든 대상이 들어 있으면 "전원 한 번씩"입니다.
  if (submittedIds.size !== targetIds.length || !targetIds.every((id) => submittedIds.has(id))) {
    return sendError(res, 400, 'INVALID_INPUT', '자신을 뺀 조원 전원을 한 번씩 평가해야 합니다')
  }

  // 실제 백엔드처럼 한 번에 제출한 평가는 모두 같은 시각입니다. 의견은 앞뒤 공백을 지우고, 비었으면 null입니다.
  const submittedAt = nowLocalDateTime()
  for (const item of evaluations) {
    const scores = {}
    SCORE_FIELDS.forEach((field) => { scores[field] = item[field] })
    const comment = isBlank(item.comment) ? null : item.comment.trim()
    db.get('evaluations')
      .push({ id: nextId(db, 'evaluations'), evaluatorId: evaluator.id, targetId: item.targetTeamMemberId, ...scores, comment, submittedAt })
      .write()
  }
  // 제출한 뒤에는 임시 저장이 필요 없어서 지웁니다.
  db.get('evaluationDrafts').remove({ evaluatorId: evaluator.id }).write()
  const myEvaluations = db.get('evaluations').filter({ evaluatorId: evaluator.id }).value()
  return res.json(toEvaluationStatusResponse(myEvaluations))
}

// 근거 패널의 완료확인 한 건입니다. 실제 EvidenceConfirmationResponse와 같습니다. 어떤 기록의 확인인지 기록 전체를 함께 담습니다.
function toEvidenceConfirmationResponse(db, confirmation) {
  const { id, deliverableId, contributionId, confirmerMemberId, confirmerName, confirmedAt, canceledAt } = toConfirmationResponse(db, confirmation)
  const deliverable = deliverableId === null ? null : toDeliverableResponse(db, db.get('deliverables').find({ id: deliverableId }).value())
  const contribution = contributionId === null ? null : toContributionResponse(db, db.get('contributions').find({ id: contributionId }).value())
  return { id, deliverable, contribution, confirmerMemberId, confirmerName, confirmedAt, canceledAt }
}

// GET /teams/{teamId}/members/{teamMemberId}/evidence: 한 조원의 기록을 종류별로 돌려줍니다. 건수는 담지 않습니다. 검사 순서는 실제 백엔드와 같습니다.
function handleGetEvidence(req, res, loginMember, teamId, teamMemberId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team) return
  // 과제를 만든 교수는 단계와 상관없이, 같은 조 조원은 평가 단계에서만 봅니다.
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  if (assignment.professorId !== loginMember.id) {
    if (!checkTeamMember(db, res, team, loginMember)) return
    if (assignment.phase !== 'EVALUATION') {
      return sendError(res, 400, 'INVALID_INPUT', '근거 패널은 평가 단계에서 볼 수 있습니다')
    }
  }
  const target = db.get('teamMembers').find({ id: teamMemberId, teamId }).value()
  if (!target) {
    return sendError(res, 404, 'NOT_FOUND', '이 조의 조원이 아닙니다. id=' + teamMemberId)
  }

  const deliverables = db.get('deliverables').filter({ teamMemberId }).sortBy('id').reverse().value()
  const contributions = db.get('contributions').filter({ assigneeId: teamMemberId }).sortBy('id').reverse().value()
  const myDeliverableIds = deliverables.map((deliverable) => deliverable.id)
  const myContributionIds = contributions.map((contribution) => contribution.id)
  const confirmations = db.get('confirmations').sortBy('id').reverse().value()
  const received = confirmations.filter((confirmation) => myDeliverableIds.includes(confirmation.deliverableId) || myContributionIds.includes(confirmation.contributionId))
  const given = confirmations.filter((confirmation) => confirmation.confirmerId === teamMemberId)
  return res.json({
    deliverables: deliverables.map((deliverable) => toDeliverableResponse(db, deliverable)),
    contributions: contributions.map((contribution) => toContributionResponse(db, contribution)),
    receivedConfirmations: received.map((confirmation) => toEvidenceConfirmationResponse(db, confirmation)),
    givenConfirmations: given.map((confirmation) => toEvidenceConfirmationResponse(db, confirmation)),
  })
}

// 기록 열람의 한 줄을 만듭니다. 실제 RecordResponse와 같은 모양이고 값이 없는 칸은 null입니다.
function toRecordRow(type, occurredAt, values) {
  return {
    type,
    occurredAt,
    teamMemberId: null,
    memberName: null,
    targetTeamMemberId: null,
    targetMemberName: null,
    title: null,
    roleName: null,
    previousRoleName: null,
    deliverableId: null,
    contributionId: null,
    ...values,
  }
}

// 조 참여 번호로 { teamMemberId, memberName } 칸을 만듭니다.
function memberCellsOf(db, teamMemberId) {
  const teamMember = db.get('teamMembers').find({ id: teamMemberId }).value()
  const member = db.get('members').find({ id: teamMember.memberId }).value()
  return { teamMemberId, memberName: member.name }
}

// 조 참여 번호로 { targetTeamMemberId, targetMemberName } 칸을 만듭니다.
function targetCellsOf(db, teamMemberId) {
  const { memberName } = memberCellsOf(db, teamMemberId)
  return { targetTeamMemberId: teamMemberId, targetMemberName: memberName }
}

// 완료확인 한 건의 줄입니다. 취소했으면 취소 줄을 먼저 넣습니다.
function toConfirmationRecordRows(db, confirmation) {
  const deliverable = confirmation.deliverableId === null ? null : db.get('deliverables').find({ id: confirmation.deliverableId }).value()
  const contribution = confirmation.contributionId === null ? null : db.get('contributions').find({ id: confirmation.contributionId }).value()
  const values = {
    ...memberCellsOf(db, confirmation.confirmerId),
    ...targetCellsOf(db, deliverable ? deliverable.teamMemberId : contribution.assigneeId),
    title: deliverable ? deliverable.fileName : contribution.title,
    deliverableId: confirmation.deliverableId,
    contributionId: confirmation.contributionId,
  }
  const rows = []
  if (confirmation.canceledAt !== null) rows.push(toRecordRow('CONFIRMATION_CANCELED', confirmation.canceledAt, values))
  rows.push(toRecordRow('CONFIRMATION_CREATED', confirmation.confirmedAt, values))
  return rows
}

// 기여 항목 한 건의 줄입니다. 나중에 일어나는 일의 줄을 먼저 넣습니다.
function toContributionRecordRows(db, contribution) {
  const values = { ...memberCellsOf(db, contribution.assigneeId), title: contribution.title, contributionId: contribution.id }
  const rows = []
  if (contribution.canceledAt !== null) rows.push(toRecordRow('CONTRIBUTION_CANCELED', contribution.canceledAt, values))
  if (contribution.reportedAt !== null) rows.push(toRecordRow('CONTRIBUTION_REPORTED', contribution.reportedAt, values))
  rows.push(toRecordRow('CONTRIBUTION_CREATED', contribution.createdAt, values))
  return rows
}

// 산출물 한 건의 줄입니다. 삭제했으면 삭제 줄을 먼저 넣습니다.
function toDeliverableRecordRows(db, deliverable) {
  const values = {
    ...memberCellsOf(db, deliverable.teamMemberId),
    title: deliverable.fileName,
    roleName: deliverable.roleName,
    deliverableId: deliverable.id,
  }
  const rows = []
  if (deliverable.deletedAt !== null) rows.push(toRecordRow('DELIVERABLE_DELETED', deliverable.deletedAt, values))
  rows.push(toRecordRow('DELIVERABLE_UPLOADED', deliverable.uploadedAt, values))
  return rows
}

// 한 조원의 역할 줄입니다. 이력은 최근 것부터이므로 지금 역할에서 시작해 한 칸씩 과거로 갑니다. 첫 지정 시각은 가장 오래된 이력에서 읽습니다.
function toRoleRecordRows(db, teamMember) {
  const memberCells = memberCellsOf(db, teamMember.id)
  const histories = db.get('roleHistories').filter({ teamMemberId: teamMember.id }).sortBy('id').reverse().value()
  const rows = []
  let roleName = teamMember.roleName
  let assignedAt = teamMember.roleAssignedAt
  histories.forEach((history) => {
    rows.push(toRecordRow('ROLE_CHANGED', history.changedAt, { ...memberCells, roleName, previousRoleName: history.previousRoleName }))
    roleName = history.previousRoleName
    assignedAt = history.previousRoleAssignedAt
  })
  if (roleName !== null) rows.push(toRecordRow('ROLE_ASSIGNED', assignedAt, { ...memberCells, roleName }))
  return rows
}

// GET /teams/{teamId}/records: 한 조의 기록을 한 줄씩 펼쳐 최근 것부터 돌려줍니다. 동료평가와 임시 저장은 넣지 않습니다. 넣는 순서는 실제 RecordResponse와 같습니다.
function handleGetRecords(req, res, loginMember, teamId) {
  const db = req.app.db
  const team = findTeamOrSendError(db, res, teamId)
  if (!team || !checkTeamAccess(db, res, team, loginMember)) return

  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  const teamMembers = db.get('teamMembers').filter({ teamId }).sortBy('id').value()
  const teamMemberIds = teamMembers.map((teamMember) => teamMember.id)
  const newestFirst = (collection) => db.get(collection).sortBy('id').reverse().value()

  const rows = []
  if (assignment.evaluationStartedAt !== null) rows.push(toRecordRow('EVALUATION_STARTED', assignment.evaluationStartedAt, {}))
  newestFirst('leaderHistories')
    .filter((history) => history.teamId === teamId)
    .forEach((history) => rows.push(toRecordRow('LEADER_CHANGED', history.changedAt, {
      ...memberCellsOf(db, history.newLeaderId),
      ...targetCellsOf(db, history.previousLeaderId),
    })))
  newestFirst('confirmations')
    .filter((confirmation) => teamMemberIds.includes(confirmation.confirmerId))
    .forEach((confirmation) => rows.push(...toConfirmationRecordRows(db, confirmation)))
  newestFirst('contributions')
    .filter((contribution) => contribution.teamId === teamId)
    .forEach((contribution) => rows.push(...toContributionRecordRows(db, contribution)))
  newestFirst('deliverables')
    .filter((deliverable) => teamMemberIds.includes(deliverable.teamMemberId))
    .forEach((deliverable) => rows.push(...toDeliverableRecordRows(db, deliverable)))
  teamMembers.forEach((teamMember) => rows.push(...toRoleRecordRows(db, teamMember)))

  // sort는 시각이 같은 줄의 순서를 바꾸지 않습니다. 시각은 같은 모양의 글자라 글자 비교가 곧 시간 비교입니다.
  rows.sort((a, b) => (a.occurredAt === b.occurredAt ? 0 : a.occurredAt < b.occurredAt ? 1 : -1))
  return res.json(rows)
}

// 조를 찾고 과제를 만든 교수인지 확인합니다. 아니면 오류를 보내고 undefined를 돌려줍니다. 실제 TeamAccessChecker.checkProfessor와 같습니다.
function findTeamForProfessorOrSendError(db, res, teamId, loginMember) {
  const team = findTeamOrSendError(db, res, teamId)
  if (!team) return undefined
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  if (assignment.professorId !== loginMember.id) {
    sendError(res, 403, 'FORBIDDEN', '과제를 만든 교수만 볼 수 있습니다')
    return undefined
  }
  return team
}

// 한 조에서 제출된 평가를 제출한 순서대로 찾습니다. 평가자가 그 조의 조원인 줄만 고릅니다. 임시 저장은 evaluations에 없습니다.
function findTeamEvaluations(db, teamId) {
  const teamMemberIds = db.get('teamMembers').filter({ teamId }).map('id').value()
  return db.get('evaluations').sortBy('id').value().filter((evaluation) => teamMemberIds.includes(evaluation.evaluatorId))
}

// GET /teams/{teamId}/evaluations/status: 조원별 제출 여부를 돌려줍니다. 몇 명이 제출했는지는 세지 않습니다.
function handleGetEvaluationSubmissions(req, res, loginMember, teamId) {
  const db = req.app.db
  if (!findTeamForProfessorOrSendError(db, res, teamId, loginMember)) return
  const evaluations = findTeamEvaluations(db, teamId)
  const teamMembers = db.get('teamMembers').filter({ teamId }).sortBy('id').value()
  return res.json(teamMembers.map((teamMember) => {
    const mine = evaluations.find((evaluation) => evaluation.evaluatorId === teamMember.id)
    return { teamMemberId: teamMember.id, name: findTeamMemberName(db, teamMember.id), submitted: mine !== undefined, submittedAt: mine ? mine.submittedAt : null }
  }))
}

// GET /teams/{teamId}/evaluations: 평가자별 점수와 의견을 입력된 그대로 돌려줍니다. 합계, 평균, 순위는 없습니다.
function handleGetTeamEvaluations(req, res, loginMember, teamId) {
  const db = req.app.db
  if (!findTeamForProfessorOrSendError(db, res, teamId, loginMember)) return
  return res.json(findTeamEvaluations(db, teamId).map((evaluation) => ({
    id: evaluation.id,
    evaluatorTeamMemberId: evaluation.evaluatorId,
    evaluatorName: findTeamMemberName(db, evaluation.evaluatorId),
    targetTeamMemberId: evaluation.targetId,
    targetName: findTeamMemberName(db, evaluation.targetId),
    goalSharing: evaluation.goalSharing,
    roleFulfillment: evaluation.roleFulfillment,
    encouragement: evaluation.encouragement,
    onlineCommunication: evaluation.onlineCommunication,
    offlineCommunication: evaluation.offlineCommunication,
    progressCheck: evaluation.progressCheck,
    comment: evaluation.comment,
    submittedAt: evaluation.submittedAt,
  })))
}

// 사람이 쓴 글자를 CSV 칸 하나로 바꿉니다. 실제 EvaluationCsv.textCell과 같습니다.
function toCsvTextCell(text) {
  if (text === null) return ''
  // =, +, -, @로 시작하면 Excel이 수식으로 실행합니다. 앞에 '를 붙이면 글자로 읽습니다.
  const cell = /^[=+\-@]/.test(text) ? "'" + text : text
  // 쉼표, 큰따옴표, 줄바꿈이 있으면 칸이 나뉘지 않게 큰따옴표로 감쌉니다. 안의 큰따옴표는 두 번 씁니다.
  return /[",\n\r]/.test(cell) ? '"' + cell.replace(/"/g, '""') + '"' : cell
}

// GET /teams/{teamId}/evaluations/export: 평가 결과를 CSV 파일로 내려줍니다. 맨 앞의 BOM이 있어야 Excel이 한글을 읽습니다. 합계, 평균, 순위 열은 없습니다.
function handleExportEvaluations(req, res, loginMember, teamId) {
  const db = req.app.db
  if (!findTeamForProfessorOrSendError(db, res, teamId, loginMember)) return
  const lines = ['평가자,대상,목표 공유·협업,역할 수행,격려·도움,온라인 소통,대면 소통,진행 점검·보충,의견,제출 시각']
  findTeamEvaluations(db, teamId).forEach((evaluation) => {
    lines.push([
      toCsvTextCell(findTeamMemberName(db, evaluation.evaluatorId)),
      toCsvTextCell(findTeamMemberName(db, evaluation.targetId)),
      ...SCORE_FIELDS.map((field) => evaluation[field]),
      toCsvTextCell(evaluation.comment),
      evaluation.submittedAt.replace('T', ' '),
    ].join(','))
  })
  res.attachment('evaluations-team-' + teamId + '.csv')
  return res.send(Buffer.from('\uFEFF' + lines.map((line) => line + '\r\n').join(''), 'utf8'))
}

// 알림 한 건의 모양입니다. 실제 NotificationResponse와 같습니다. 받는 회원(recipientId)은 응답에 담지 않습니다.
function toNotificationResponse(notification) {
  const { id, type, message, teamId, createdAt, readAt } = notification
  return { id, type, message, teamId, createdAt, readAt }
}

// 알림 한 건을 저장합니다. 실제 NotificationService.save와 같습니다.
function saveNotification(db, recipientId, type, message, teamId) {
  db.get('notifications').push({
    id: nextId(db, 'notifications'),
    recipientId,
    type,
    message,
    teamId,
    createdAt: nowLocalDateTime(),
    readAt: null,
  }).write()
}

// 알림 문장 앞에 붙일 "[과제 제목 조 이름] "입니다.
function notificationPrefixOf(db, team) {
  const assignment = db.get('assignments').find({ id: team.assignmentId }).value()
  return '[' + assignment.title + ' ' + team.name + '] '
}

// 평가가 시작된 과제의 모든 조원(조장 포함)에게 알림을 저장합니다. 실제 백엔드처럼 조 참여 번호 순서입니다.
function notifyEvaluationStarted(db, assignment) {
  const teams = db.get('teams').filter({ assignmentId: assignment.id }).value()
  const teamIds = teams.map((team) => team.id)
  const teamMembers = db.get('teamMembers').filter((teamMember) => teamIds.includes(teamMember.teamId)).sortBy('id').value()
  teamMembers.forEach((teamMember) => {
    const team = teams.find((item) => item.id === teamMember.teamId)
    saveNotification(db, teamMember.memberId, 'EVALUATION_STARTED',
      notificationPrefixOf(db, team) + '평가가 시작되었습니다. 동료평가를 입력할 수 있습니다.', team.id)
  })
}

// 기여 항목의 담당 조원에게 알림을 저장합니다. 조장이 자신을 담당으로 정했으면 만들지 않습니다.
function notifyContributionAssigned(db, contribution, leaderMemberId) {
  const assignee = db.get('teamMembers').find({ id: contribution.assigneeId }).value()
  if (assignee.memberId === leaderMemberId) return
  const team = db.get('teams').find({ id: contribution.teamId }).value()
  saveNotification(db, assignee.memberId, 'CONTRIBUTION_ASSIGNED',
    notificationPrefixOf(db, team) + '기여 항목 "' + contribution.title + '"의 담당이 되었습니다.', team.id)
}

// 완료 보고된 항목의 조에서 보고한 조원을 뺀 조원 전원(조장 포함)에게 알림을 저장합니다. 보고 메모는 담지 않습니다.
function notifyContributionReported(db, contribution) {
  const team = db.get('teams').find({ id: contribution.teamId }).value()
  const reporter = db.get('teamMembers').find({ id: contribution.assigneeId }).value()
  const reporterMember = db.get('members').find({ id: reporter.memberId }).value()
  const message = notificationPrefixOf(db, team) + reporterMember.name + ' 님이 기여 항목 "' + contribution.title + '" 완료 보고를 올렸습니다.'
  const others = db.get('teamMembers').filter({ teamId: team.id }).sortBy('id').value()
    .filter((teamMember) => teamMember.id !== reporter.id)
  others.forEach((teamMember) => saveNotification(db, teamMember.memberId, 'CONTRIBUTION_REPORTED', message, team.id))
}

// GET /notifications: 로그인한 회원이 받은 알림을 최근 것부터 돌려줍니다. 다른 회원의 알림은 담지 않습니다.
function handleGetNotifications(req, res, loginMember) {
  const notifications = req.app.db.get('notifications').filter({ recipientId: loginMember.id }).sortBy('id').reverse().value()
  return res.json(notifications.map(toNotificationResponse))
}

// POST /notifications/{notificationId}/read: 받은 회원 본인이 알림을 읽음으로 표시합니다. 이미 읽었으면 처음 시각을 그대로 둡니다.
function handleReadNotification(req, res, loginMember, notificationId) {
  const db = req.app.db
  const notification = db.get('notifications').find({ id: notificationId }).value()
  if (!notification) {
    return sendError(res, 404, 'NOT_FOUND', '알림이 없습니다. id=' + notificationId)
  }
  if (notification.recipientId !== loginMember.id) {
    return sendError(res, 403, 'FORBIDDEN', '받은 회원만 읽음으로 표시할 수 있습니다')
  }
  if (notification.readAt === null) {
    db.get('notifications').find({ id: notificationId }).assign({ readAt: nowLocalDateTime() }).write()
  }
  return res.json(toNotificationResponse(db.get('notifications').find({ id: notificationId }).value()))
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
function handleRequest(req, res, next) {
  if (req.method === 'POST' && req.path === '/auth/login') {
    return handleLogin(req, res)
  }
  if (req.method === 'POST' && req.path === '/auth/google') {
    return handleGoogleLogin(req, res)
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
  if (req.method === 'GET' && req.path === '/notifications') {
    return handleGetNotifications(req, res, loginMember)
  }

  const readNotificationIdText = matchId(/^\/notifications\/([^/]+)\/read$/, req.path)
  if (req.method === 'POST' && readNotificationIdText !== null) {
    const notificationId = parseIdOrSendError(res, readNotificationIdText)
    if (notificationId === null) return
    return handleReadNotification(req, res, loginMember, notificationId)
  }

  const assignmentIdText = matchId(/^\/assignments\/([^/]+)$/, req.path)
  if (req.method === 'GET' && assignmentIdText !== null) {
    const assignmentId = parseIdOrSendError(res, assignmentIdText)
    if (assignmentId === null) return
    return handleGetAssignment(req, res, loginMember, assignmentId)
  }

  const startEvaluationIdText = matchId(/^\/assignments\/([^/]+)\/start-evaluation$/, req.path)
  if (req.method === 'POST' && startEvaluationIdText !== null) {
    const assignmentId = parseIdOrSendError(res, startEvaluationIdText)
    if (assignmentId === null) return
    return handleStartEvaluation(req, res, loginMember, assignmentId)
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

  const deliverablesTeamIdText = matchId(/^\/teams\/([^/]+)\/deliverables$/, req.path)
  if (deliverablesTeamIdText !== null && (req.method === 'GET' || req.method === 'POST')) {
    const teamId = parseIdOrSendError(res, deliverablesTeamIdText)
    if (teamId === null) return
    if (req.method === 'GET') return handleGetDeliverables(req, res, loginMember, teamId)
    return handleUploadDeliverable(req, res, next, loginMember, teamId)
  }

  const deleteIdText = matchId(/^\/deliverables\/([^/]+)$/, req.path)
  if (req.method === 'DELETE' && deleteIdText !== null) {
    const deliverableId = parseIdOrSendError(res, deleteIdText)
    if (deliverableId === null) return
    return handleDeleteDeliverable(req, res, loginMember, deliverableId)
  }

  const downloadIdText = matchId(/^\/deliverables\/([^/]+)\/download$/, req.path)
  if (req.method === 'GET' && downloadIdText !== null) {
    const deliverableId = parseIdOrSendError(res, downloadIdText)
    if (deliverableId === null) return
    return handleDownloadDeliverable(req, res, loginMember, deliverableId)
  }

  const myEvaluationTeamIdText = matchId(/^\/teams\/([^/]+)\/evaluations\/mine$/, req.path)
  if (req.method === 'GET' && myEvaluationTeamIdText !== null) {
    const teamId = parseIdOrSendError(res, myEvaluationTeamIdText)
    if (teamId === null) return
    return handleGetMyEvaluationStatus(req, res, loginMember, teamId)
  }

  const evaluationDraftTeamIdText = matchId(/^\/teams\/([^/]+)\/evaluations\/draft$/, req.path)
  if ((req.method === 'GET' || req.method === 'PUT') && evaluationDraftTeamIdText !== null) {
    const teamId = parseIdOrSendError(res, evaluationDraftTeamIdText)
    if (teamId === null) return
    if (req.method === 'GET') return handleGetMyEvaluationDrafts(req, res, loginMember, teamId)
    return handleSaveEvaluationDrafts(req, res, loginMember, teamId)
  }

  const evaluationsTeamIdText = matchId(/^\/teams\/([^/]+)\/evaluations$/, req.path)
  if (req.method === 'POST' && evaluationsTeamIdText !== null) {
    const teamId = parseIdOrSendError(res, evaluationsTeamIdText)
    if (teamId === null) return
    return handleSubmitEvaluations(req, res, loginMember, teamId)
  }

  const contributionsTeamIdText = matchId(/^\/teams\/([^/]+)\/contributions$/, req.path)
  if ((req.method === 'GET' || req.method === 'POST') && contributionsTeamIdText !== null) {
    const teamId = parseIdOrSendError(res, contributionsTeamIdText)
    if (teamId === null) return
    if (req.method === 'POST') {
      return handleCreateContribution(req, res, loginMember, teamId)
    }
    return handleGetContributions(req, res, loginMember, teamId)
  }

  const reportIdText = matchId(/^\/contributions\/([^/]+)\/report$/, req.path)
  if (req.method === 'POST' && reportIdText !== null) {
    const contributionId = parseIdOrSendError(res, reportIdText)
    if (contributionId === null) return
    return handleReportContribution(req, res, loginMember, contributionId)
  }

  const cancelIdText = matchId(/^\/contributions\/([^/]+)\/cancel$/, req.path)
  if (req.method === 'POST' && cancelIdText !== null) {
    const contributionId = parseIdOrSendError(res, cancelIdText)
    if (contributionId === null) return
    return handleCancelContribution(req, res, loginMember, contributionId)
  }

  const deliverableConfirmationsIdText = matchId(/^\/deliverables\/([^/]+)\/confirmations$/, req.path)
  if ((req.method === 'GET' || req.method === 'POST') && deliverableConfirmationsIdText !== null) {
    const deliverableId = parseIdOrSendError(res, deliverableConfirmationsIdText)
    if (deliverableId === null) return
    if (req.method === 'POST') {
      return handleConfirmDeliverable(req, res, loginMember, deliverableId)
    }
    return handleGetDeliverableConfirmations(req, res, loginMember, deliverableId)
  }

  const contributionConfirmationsIdText = matchId(/^\/contributions\/([^/]+)\/confirmations$/, req.path)
  if ((req.method === 'GET' || req.method === 'POST') && contributionConfirmationsIdText !== null) {
    const contributionId = parseIdOrSendError(res, contributionConfirmationsIdText)
    if (contributionId === null) return
    if (req.method === 'POST') {
      return handleConfirmContribution(req, res, loginMember, contributionId)
    }
    return handleGetContributionConfirmations(req, res, loginMember, contributionId)
  }

  const cancelConfirmationIdText = matchId(/^\/confirmations\/([^/]+)\/cancel$/, req.path)
  if (req.method === 'POST' && cancelConfirmationIdText !== null) {
    const confirmationId = parseIdOrSendError(res, cancelConfirmationIdText)
    if (confirmationId === null) return
    return handleCancelConfirmation(req, res, loginMember, confirmationId)
  }

  const leaderTeamIdText = matchId(/^\/teams\/([^/]+)\/leader$/, req.path)
  if (req.method === 'PUT' && leaderTeamIdText !== null) {
    const teamId = parseIdOrSendError(res, leaderTeamIdText)
    if (teamId === null) return
    return handleChangeLeader(req, res, loginMember, teamId)
  }

  const leaderHistoriesTeamIdText = matchId(/^\/teams\/([^/]+)\/leader-histories$/, req.path)
  if (req.method === 'GET' && leaderHistoriesTeamIdText !== null) {
    const teamId = parseIdOrSendError(res, leaderHistoriesTeamIdText)
    if (teamId === null) return
    return handleGetLeaderHistories(req, res, loginMember, teamId)
  }

  const roleMatch = req.path.match(/^\/teams\/([^/]+)\/members\/([^/]+)\/role$/)
  if ((req.method === 'POST' || req.method === 'PUT') && roleMatch) {
    const teamId = parseIdOrSendError(res, roleMatch[1])
    if (teamId === null) return
    const teamMemberId = parseIdOrSendError(res, roleMatch[2])
    if (teamMemberId === null) return
    if (req.method === 'PUT') {
      return handleChangeRole(req, res, loginMember, teamId, teamMemberId)
    }
    return handleAssignRole(req, res, loginMember, teamId, teamMemberId)
  }

  const evaluationResultMatch = req.path.match(/^\/teams\/([^/]+)\/evaluations(\/status|\/export)?$/)
  if (req.method === 'GET' && evaluationResultMatch) {
    const teamId = parseIdOrSendError(res, evaluationResultMatch[1])
    if (teamId === null) return
    if (evaluationResultMatch[2] === '/status') return handleGetEvaluationSubmissions(req, res, loginMember, teamId)
    if (evaluationResultMatch[2] === '/export') return handleExportEvaluations(req, res, loginMember, teamId)
    return handleGetTeamEvaluations(req, res, loginMember, teamId)
  }

  const recordsMatch = req.path.match(/^\/teams\/([^/]+)\/records$/)
  if (req.method === 'GET' && recordsMatch) {
    const teamId = parseIdOrSendError(res, recordsMatch[1])
    if (teamId === null) return
    return handleGetRecords(req, res, loginMember, teamId)
  }

  const evidenceMatch = req.path.match(/^\/teams\/([^/]+)\/members\/([^/]+)\/evidence$/)
  if (req.method === 'GET' && evidenceMatch) {
    const teamId = parseIdOrSendError(res, evidenceMatch[1])
    if (teamId === null) return
    const teamMemberId = parseIdOrSendError(res, evidenceMatch[2])
    if (teamMemberId === null) return
    return handleGetEvidence(req, res, loginMember, teamId, teamMemberId)
  }

  const roleHistoriesMatch = req.path.match(/^\/teams\/([^/]+)\/members\/([^/]+)\/role-histories$/)
  if (req.method === 'GET' && roleHistoriesMatch) {
    const teamId = parseIdOrSendError(res, roleHistoriesMatch[1])
    if (teamId === null) return
    const teamMemberId = parseIdOrSendError(res, roleHistoriesMatch[2])
    if (teamMemberId === null) return
    return handleGetRoleHistories(req, res, loginMember, teamId, teamMemberId)
  }

  // 실제 API에 없는 주소입니다. json-server의 기본 주소(/members 등)로 넘기면 비밀번호까지 그대로 보이므로 막습니다.
  return sendError(res, 404, 'NOT_FOUND', '없는 주소입니다')
}

// 요청을 처리하다 난 오류를 받습니다. 실제 백엔드의 GlobalExceptionHandler에서 맨 아래 handleException과 같은 역할입니다.
// Express는 인자가 4개인 함수를 "오류 처리용"으로 알아봅니다. next는 쓰지 않아도 4개를 맞춰야 합니다.
function handleError(err, req, res, next) {
  // JSON으로 읽을 수 없는 body입니다. json-server의 body-parser가 이 오류를 냅니다. 실제 백엔드의 handleHttpMessageNotReadable과 같습니다.
  if (err.type === 'entity.parse.failed') {
    return sendError(res, 400, 'INVALID_INPUT', NOT_READABLE_MESSAGE)
  }
  // 그 밖은 가짜 API의 잘못입니다. 원인은 터미널에만 찍고, 응답에는 담지 않습니다.
  console.error(err)
  return sendError(res, 500, 'SERVER_ERROR', '서버에서 오류가 났습니다')
}

// json-server가 이 목록을 순서대로 등록합니다. 요청은 handleRequest가 처리하고, 오류가 나면 handleError로 갑니다.
module.exports = [handleRequest, handleError]
