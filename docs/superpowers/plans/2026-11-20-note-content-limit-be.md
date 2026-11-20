# 지식 메모 내용 10000자 (BE) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 지식 메모 내용의 최대 길이를 2000자에서 10000자로 올린다.

**Architecture:** 엔티티 상수 `KnowledgeNote.MAX_CONTENT_LENGTH` 하나를 기준값으로 삼는다. DTO 검증도 이 상수를 참조하게 한다(`CommentRequest` → `WorkoutComment.MAX_CONTENT_LENGTH` 패턴). DB 컬럼은 이미 `text`라 마이그레이션은 없다.

**Tech Stack:** Spring Boot, Jakarta Validation, JUnit5 + MockMvc.

**Spec:** `../FE-HCM/docs/superpowers/specs/2026-11-20-note-code-editing-design.md` ("글자 수 제한" 절)
**짝 계획:** `../FE-HCM/docs/superpowers/plans/2026-11-20-note-code-editing-fe.md`. 이 BE가 먼저 배포돼야 한다. 구 BE에서는 FE가 2000자를 넘는 내용을 보내면 400이 난다.

## Global Constraints

- 최대 길이는 `10000`이고, 오류 메시지는 `내용은 10000자 이하로 입력해주세요.`이다.
- 작업 순서: 시작 전에 BE 저장소에 GitHub 이슈를 만든다. 브랜치는 `feature/issue-{n}`이다. 커밋 메시지는 한국어로 쓰고 끝에 `(#{n})`을 붙인다. PR base는 `main`이다.
- 작업 트리의 `.DS_Store`는 커밋하지 않는다.

## Review Focus

1. 정확히 10000자인 내용 → 400 없이 통과해야 한다(경계값). → Task 1 테스트
2. 10001자인 내용 → 400을 반환해야 한다. → Task 1 테스트

---

### Task 1: 내용 길이 제한 상향

**Files:**
- Modify: `src/main/java/com/behcm/domain/knowledge/entity/KnowledgeNote.java:37`
- Modify: `src/main/java/com/behcm/domain/knowledge/dto/KnowledgeNoteRequest.java:20`
- Test: `src/test/java/com/behcm/domain/knowledge/controller/KnowledgeNoteControllerTest.java:189-200`

**Interfaces:**
- Produces: `KnowledgeNote.MAX_CONTENT_LENGTH = 10000`

- [ ] **Step 1: 테스트 수정·추가**
  - 기존 `create_withTooLongContent_returns400`을 고친다. `@DisplayName("create는 내용이 10000자를 넘으면 400을 반환한다")`로 바꾸고, 입력을 `"가".repeat(10001)`로 바꾼다.
  - 새 테스트 `create_withMaxLengthContent_passesValidation`을 추가한다. 입력은 `"가".repeat(10000)`이고, `status().isBadRequest()`가 **아님**을 확인한다.
  - 새 테스트는 같은 파일의 성공 케이스 테스트처럼 `knowledgeNoteService.create`를 stub해서 2xx를 확인한다.
- [ ] **Step 2: 실패 확인**
  - Run: `./gradlew test --tests "*KnowledgeNoteControllerTest"`
  - Expected: 새 테스트 FAIL(400)
- [ ] **Step 3: 구현**
  - `MAX_CONTENT_LENGTH`를 `10000`으로 바꾼다.
  - DTO를 `@Size(max = KnowledgeNote.MAX_CONTENT_LENGTH, message = "내용은 10000자 이하로 입력해주세요.")`로 바꾼다.
- [ ] **Step 4: 통과 확인**
  - Run: `./gradlew test --tests "*KnowledgeNote*"`를 돌린 뒤 `./gradlew build`를 돌린다.
  - Expected: 둘 다 PASS / BUILD SUCCESSFUL
- [ ] **Step 5: Commit**
  - `feat: 지식 메모 내용 최대 길이를 10000자로 상향 (#{n})`
