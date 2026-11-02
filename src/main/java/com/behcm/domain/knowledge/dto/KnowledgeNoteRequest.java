package com.behcm.domain.knowledge.dto;

import com.behcm.domain.knowledge.entity.KnowledgeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeNoteRequest {

    @NotNull(message = "분야는 필수입니다.")
    private KnowledgeCategory category;

    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 2000, message = "내용은 2000자 이하로 입력해주세요.")
    private String content;

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
}
