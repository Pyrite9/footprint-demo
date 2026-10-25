package com.footprint.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// member 테이블과 대응합니다. 교수와 학생 모두 회원입니다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 아이디로 씁니다. unique 제약으로 같은 이메일이 두 번 저장되지 않습니다.
    @Column(nullable = false, unique = true)
    private String email;

    // 입력한 비밀번호 그대로가 아니라 BCrypt로 바꾼 값을 저장합니다.
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    // enum을 순서 번호(0, 1)가 아니라 이름(PROFESSOR, STUDENT)으로 저장합니다. enum 순서를 바꿔도 데이터가 어긋나지 않습니다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberType memberType;

    public Member(String email, String password, String name, MemberType memberType) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.memberType = memberType;
    }
}
