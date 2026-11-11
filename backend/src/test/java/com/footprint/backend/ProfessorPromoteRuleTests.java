package com.footprint.backend;

import com.footprint.backend.entity.Member;
import com.footprint.backend.entity.MemberType;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.security.GoogleTokenVerifier;
import com.footprint.backend.security.GoogleTokenVerifier.GoogleAccount;
import com.footprint.backend.security.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 교수 회원 유형 변경(X-1) 규칙을 실제 요청으로 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 구글 로그인할 때마다 교수 목록에 있는 학생을 교수로 올립니다. 내리지 않고, 조 참여 기록이 있는 학생은 올리지 않습니다.
// 교수 목록과 허용 도메인은 이 테스트에서만 가짜 주소(example.com)로 바꿉니다. student01은 데모 조의 조장이라 목록에 넣었습니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "google.allowed-domains=example.com",
        "google.professor-emails=listed@example.com, Mixed.Case@Example.com, student01@example.com"
})
class ProfessorPromoteRuleTests {

    // 진짜 구글 ID token은 테스트에서 만들 수 없어서, 검증기를 Mockito 대역으로 바꿉니다.
    // 대역은 when(...)으로 정한 credential에만 계정 정보를 돌려줍니다. 도메인·교수 판별은 실제 AuthService가 합니다.
    @MockitoBean
    private GoogleTokenVerifier googleTokenVerifier;

    private final MockMvc mockMvc;
    private final TestApi api;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;

    @Autowired
    ProfessorPromoteRuleTests(MockMvc mockMvc, MemberRepository memberRepository, JwtProvider jwtProvider) {
        this.mockMvc = mockMvc;
        this.api = new TestApi(mockMvc);
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
    }

    // 목록에 있는 학생이 다시 구글 로그인하면 교수가 됩니다. 그 로그인의 token에도 교수가 담기고, 과제를 만들 수 있습니다.
    @Test
    void listedStudentBecomesProfessorOnNextGoogleLogin() throws Exception {
        Member member = saveStudent("listed@example.com");

        String token = googleLogin("listed@example.com");

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getMemberType()).isEqualTo(MemberType.PROFESSOR);
        assertThat(memberTypeClaim(token)).isEqualTo("PROFESSOR");
        api.get("/members/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberType").value("PROFESSOR"));
        api.post("/assignments", token, """
                { "title": "올린 교수의 과제", "startDate": "2026-11-02", "endDate": "2026-12-11", "leaderEmails": ["student09@example.com"] }
                """).andExpect(status().isOk());
    }

    // 목록에 없는 학생은 그대로 학생입니다.
    @Test
    void unlistedStudentStaysStudent() throws Exception {
        Member member = saveStudent("unlisted@example.com");

        String token = googleLogin("unlisted@example.com");

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getMemberType()).isEqualTo(MemberType.STUDENT);
        assertThat(memberTypeClaim(token)).isEqualTo("STUDENT");
    }

    // 교수는 목록에 없어도 교수로 남습니다. 내리지 않습니다.
    @Test
    void professorIsNotDemotedWhenUnlisted() throws Exception {
        Member member = memberRepository.save(new Member("former@example.com", null, "목록에서 빠진 교수", MemberType.PROFESSOR));

        String token = googleLogin("former@example.com");

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getMemberType()).isEqualTo(MemberType.PROFESSOR);
        assertThat(memberTypeClaim(token)).isEqualTo("PROFESSOR");
    }

    // 목록과 이메일의 대소문자가 달라도 같은 이메일로 봅니다.
    @Test
    void emailCaseIsIgnored() throws Exception {
        Member member = saveStudent("mixed.case@example.com");

        googleLogin("mixed.case@example.com");

        assertThat(memberRepository.findById(member.getId()).orElseThrow().getMemberType()).isEqualTo(MemberType.PROFESSOR);
    }

    // 조원·조장 기록이 있는 학생은 목록에 있어도 학생으로 로그인합니다. 오류는 내지 않습니다.
    @Test
    void studentWithTeamRecordIsNotPromoted() throws Exception {
        String token = googleLogin("student01@example.com");

        assertThat(memberRepository.findByEmail("student01@example.com").orElseThrow().getMemberType()).isEqualTo(MemberType.STUDENT);
        api.get("/members/me", token).andExpect(jsonPath("$.memberType").value("STUDENT"));
        // 조원으로 보던 데모 과제를 그대로 봅니다.
        api.get("/assignments", token).andExpect(jsonPath("$[?(@.title == '발자국 데모 과제')]").exists());
    }

    // 목록에 있는 이메일로 처음 로그인하면 처음부터 교수로 만듭니다. 목록에 없으면 학생입니다.
    @Test
    void newMemberTypeFollowsList() throws Exception {
        assertThat(memberTypeClaim(googleLogin("listed@example.com"))).isEqualTo("PROFESSOR");
        assertThat(memberTypeClaim(googleLogin("newcomer@example.com"))).isEqualTo("STUDENT");
    }

    // 올리기 전에 받은 token에는 STUDENT가 들어 있지만, 서버는 DB의 회원 유형으로 권한을 봅니다. 그래서 옛 token으로도 교수입니다.
    @Test
    void oldStudentTokenUsesProfessorTypeFromDatabase() throws Exception {
        Member member = saveStudent("listed@example.com");
        String oldToken = jwtProvider.createToken(member);
        assertThat(memberTypeClaim(oldToken)).isEqualTo("STUDENT");

        googleLogin("listed@example.com");

        api.get("/members/me", oldToken).andExpect(jsonPath("$.memberType").value("PROFESSOR"));
        api.post("/assignments", oldToken, """
                { "title": "옛 token의 과제", "startDate": "2026-11-02", "endDate": "2026-12-11", "leaderEmails": ["student09@example.com"] }
                """).andExpect(status().isOk());
    }

    // 이메일·비밀번호 로그인은 유형을 바꾸지 않습니다. student01은 목록에 있지만 이 로그인으로는 확인하지 않습니다.
    @Test
    void passwordLoginDoesNotChangeType() throws Exception {
        api.login("student01@example.com");

        assertThat(memberRepository.findByEmail("student01@example.com").orElseThrow().getMemberType()).isEqualTo(MemberType.STUDENT);
    }

    // 구글 로그인 이전에 다른 경로로 저장된 학생 회원입니다(목록에 넣기 전에 로그인한 사람을 흉내 냅니다).
    private Member saveStudent(String email) {
        return memberRepository.save(new Member(email, null, "시험 학생", MemberType.STUDENT));
    }

    // 대역 검증기가 이 이메일의 학교 계정을 돌려주게 하고 POST /auth/google을 보냅니다. 받은 token을 돌려줍니다.
    private String googleLogin(String email) throws Exception {
        String credential = "fake-credential-" + email;
        when(googleTokenVerifier.verify(credential)).thenReturn(new GoogleAccount(email, "시험 계정", "example.com"));
        String body = """
                { "credential": "%s" }
                """.formatted(credential);
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/auth/google").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    // token 가운데 부분(payload)을 풀어 memberType claim을 읽습니다. 서명은 서버가 만든 것이라 여기서 확인하지 않습니다.
    private static String memberTypeClaim(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return JsonPath.read(payload, "$.memberType");
    }
}
