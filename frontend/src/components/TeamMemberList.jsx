// 조원 목록을 이름과 역할로 그립니다. 역할을 배정하기 전이면 "역할 없음"으로 보입니다. 데이터는 page에서 props로 받습니다.
function TeamMemberList({ members }) {
    return (
        <ul>
            {members.map((member) => (
                <li key={member.id}>
                    {member.name}
                    {member.leader && ' (조장)'}
                    {' · '}
                    {member.roleName ?? '역할 없음'}
                </li>
            ))}
        </ul>
    )
}

export default TeamMemberList
