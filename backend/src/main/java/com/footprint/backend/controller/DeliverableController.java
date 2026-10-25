package com.footprint.backend.controller;

import com.footprint.backend.dto.DeliverableResponse;
import com.footprint.backend.entity.Deliverable;
import com.footprint.backend.service.DeliverableService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

// 산출물 요청을 받아 Service를 호출합니다. 주소가 /teams/..와 /deliverables/.. 두 갈래라서 클래스에 @RequestMapping을 두지 않습니다.
@RestController
public class DeliverableController {

    private final DeliverableService deliverableService;

    public DeliverableController(DeliverableService deliverableService) {
        this.deliverableService = deliverableService;
    }

    // 파일 하나를 올리고 저장된 산출물을 돌려줍니다.
    // @RequestParam("file"): multipart/form-data 요청에서 이름이 file인 칸을 받습니다. 빠졌으면 400입니다.
    @PostMapping("/teams/{teamId}/deliverables")
    public DeliverableResponse upload(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId,
                                      @RequestParam("file") MultipartFile file) {
        return DeliverableResponse.from(deliverableService.upload(teamId, file, memberId));
    }

    // 조의 산출물 목록을 최신순으로 돌려줍니다.
    @GetMapping("/teams/{teamId}/deliverables")
    public List<DeliverableResponse> findAll(@AuthenticationPrincipal Long memberId, @PathVariable Long teamId) {
        return deliverableService.findAll(teamId, memberId).stream()
                .map(DeliverableResponse::from)
                .toList();
    }

    // 파일 본문을 내려줍니다. JSON이 아니라 파일 자체를 body로 보내므로 ResponseEntity<Resource>를 씁니다.
    @GetMapping("/deliverables/{deliverableId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal Long memberId, @PathVariable Long deliverableId) {
        Deliverable deliverable = deliverableService.findForDownload(deliverableId, memberId);
        Resource file = deliverableService.loadFile(deliverable);

        // 파일 종류는 확장자로 짐작합니다(예: .pdf → application/pdf). 모르면 "그냥 파일"(octet-stream)입니다.
        MediaType contentType = MediaTypeFactory.getMediaType(deliverable.getFileName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        // attachment: 브라우저가 열지 않고 내려받습니다. UTF-8을 넘기면 filename*=UTF-8''... (RFC 5987) 모양이 되어 한글 이름이 깨지지 않습니다.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(deliverable.getFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(deliverable.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file);
    }
}
