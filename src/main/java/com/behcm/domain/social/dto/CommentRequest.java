package com.behcm.domain.social.dto;

import com.behcm.domain.social.entity.WorkoutComment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentRequest {

    @NotBlank(message = "댓글 내용은 필수입니다.")
    @Size(max = WorkoutComment.MAX_CONTENT_LENGTH, message = "댓글은 500자를 넘을 수 없습니다.")
    private String content;
}
