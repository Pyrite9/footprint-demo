package com.footprint.backend;

import com.footprint.backend.entity.Team;
import com.footprint.backend.exception.NotFoundException;
import com.footprint.backend.repository.DeliverableRepository;
import com.footprint.backend.repository.MemberRepository;
import com.footprint.backend.repository.TeamRepository;
import com.footprint.backend.service.AssignmentPhaseChecker;
import com.footprint.backend.service.DeliverableService;
import com.footprint.backend.service.StorageService;
import com.footprint.backend.service.TeamAccessChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 업로드 중 데이터베이스 저장이 실패하면 올린 파일을 지우는지 확인합니다. PostgreSQL과 MinIO가 켜져 있어야 합니다.
// 실제 요청으로는 데이터베이스 저장을 실패시킬 수 없어서, 저장소만 가짜(mock)로 바꾼 Service를 직접 만들어 부릅니다.
// mock: 정해 준 대로만 동작하는 가짜 객체입니다. spy: 진짜 객체를 그대로 쓰면서 어떤 메서드가 불렸는지 기록합니다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeliverableOrphanFileTests {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final TeamAccessChecker teamAccessChecker;
    private final AssignmentPhaseChecker assignmentPhaseChecker;
    private final StorageService storageService;

    private DeliverableRepository failingRepository;
    private Team team;
    private Long uploaderId;
    private MockMultipartFile file;

    // 테스트 클래스는 JUnit이 만들기 때문에, 생성자 주입을 하려면 생성자에 @Autowired를 붙여야 합니다.
    @Autowired
    DeliverableOrphanFileTests(TeamRepository teamRepository, MemberRepository memberRepository,
                               TeamAccessChecker teamAccessChecker, AssignmentPhaseChecker assignmentPhaseChecker,
                               StorageService storageService) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.teamAccessChecker = teamAccessChecker;
        this.assignmentPhaseChecker = assignmentPhaseChecker;
        this.storageService = storageService;
    }

    // 저장하면 항상 실패하는 가짜 저장소와, 시작 데이터의 1조 조원(student02)이 올릴 파일을 준비합니다.
    @BeforeEach
    void prepare() {
        failingRepository = mock(DeliverableRepository.class);
        when(failingRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("저장 실패"));
        team = teamRepository.findByInviteCode("demo-team-1").orElseThrow();
        uploaderId = memberRepository.findByEmail("student02@example.com").orElseThrow().getId();
        file = new MockMultipartFile("file", "시험.txt", "text/plain", "주인 없는 파일 시험".getBytes(StandardCharsets.UTF_8));
    }

    // 데이터베이스 저장이 실패하면 방금 올린 파일이 지워지고, 원래 오류가 그대로 나갑니다.
    @Test
    void failedSaveDeletesUploadedFile() {
        StorageService spyStorage = spy(storageService);

        assertThrows(IllegalStateException.class, () -> serviceWith(spyStorage).upload(team.getId(), file, uploaderId));

        // ArgumentCaptor: delete에 넘어간 값을 꺼내 봅니다. 그 key의 파일이 정말 사라졌는지 진짜 저장소에서 확인합니다.
        ArgumentCaptor<String> storageKey = ArgumentCaptor.forClass(String.class);
        verify(spyStorage).delete(storageKey.capture());
        assertThrows(NotFoundException.class, () -> storageService.load(storageKey.getValue()));
    }

    // 파일 지우기까지 실패해도 사용자에게 나가는 오류는 원래 오류입니다. 지우기 실패는 함께 담겨 서버 로그에 남습니다.
    @Test
    void failedDeleteKeepsOriginalError() {
        StorageService spyStorage = spy(storageService);
        doThrow(new IllegalArgumentException("지우기 실패")).when(spyStorage).delete(anyString());

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> serviceWith(spyStorage).upload(team.getId(), file, uploaderId));

        assertEquals("저장 실패", thrown.getMessage());
        assertEquals("지우기 실패", thrown.getSuppressed()[0].getMessage());
    }

    // 저장이 실패하는 저장소와 주어진 파일 저장소로 Service를 만듭니다. 나머지는 진짜입니다.
    private DeliverableService serviceWith(StorageService storage) {
        return new DeliverableService(failingRepository, teamRepository, teamAccessChecker, assignmentPhaseChecker, storage);
    }
}
