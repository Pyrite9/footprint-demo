package com.footprint.backend.repository;

import com.footprint.backend.entity.ReportPart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

// report_part 테이블을 저장·조회합니다.
public interface ReportPartRepository extends JpaRepository<ReportPart, Long> {

    // 한 보고서의 파트를 먼저 등록한 것부터 찾습니다. 합본도 이 순서입니다. 취소된 파트도 빼지 않습니다.
    List<ReportPart> findByReportIdOrderByIdAsc(Long reportId);

    // 파트를 찾으면서 그 행을 잠급니다. 다른 요청이 같은 파트를 잠그려면 이 트랜잭션이 끝날 때까지 기다립니다.
    // 메서드 이름의 "WithLock"은 읽기 쉽게 붙인 말이고, 실제 잠금은 @Lock이 합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReportPart> findWithLockById(Long id);
}
