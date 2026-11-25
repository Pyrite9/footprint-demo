package com.footprint.backend.repository;

import com.footprint.backend.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// report 테이블을 저장·조회합니다.
public interface ReportRepository extends JpaRepository<Report, Long> {

    // 한 조의 보고서를 최근에 만든 것부터 찾습니다. id가 클수록 나중에 저장한 줄입니다.
    List<Report> findByTeamIdOrderByIdDesc(Long teamId);
}
