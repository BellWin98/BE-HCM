package com.behcm.domain.knowledge.dto;

import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import com.behcm.domain.knowledge.entity.KnowledgeVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeNoteRequest {

    @NotNull(message = "분야는 필수입니다.")
    private KnowledgeCategory category;

    @Size(max = 2000, message = "내용은 2000자 이하로 입력해주세요.")
    private String content;

    // 내용·이미지 중 하나는 있어야 하지만 둘의 조합은 서비스에서 판정한다.
    @Size(max = 5, message = "이미지는 최대 5장까지 첨부할 수 있습니다.")
    private List<String> imageUrls;

    @Size(max = 2048, message = "출처 링크는 2048자 이하로 입력해주세요.")
    private String sourceUrl;

    @Size(max = 500, message = "출처 텍스트는 500자 이하로 입력해주세요.")
    private String sourceText;

    // 개수·길이는 정규화(trim, 빈값 제거, 중복 제거) 이후 서비스에서 판정한다.
    private List<String> tags;

    @Size(max = 20, message = "종목 코드는 20자 이하로 입력해주세요.")
    private String stockSymbol;

    @Size(max = 100, message = "종목명은 100자 이하로 입력해주세요.")
    private String stockName;

    // 생략하면 생성은 PUBLIC, 수정은 기존 값을 유지한다.
    private KnowledgeVisibility visibility;
}
