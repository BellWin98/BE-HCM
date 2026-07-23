package com.behcm.domain.member.controller;

import com.behcm.domain.member.dto.MemberProfileResponse;
import com.behcm.domain.member.dto.MemberSettingsResponse;
import com.behcm.domain.member.dto.ProfileImageUploadResponse;
import com.behcm.domain.member.dto.UpdateMemberProfileRequest;
import com.behcm.domain.member.dto.UpdateMemberSettingsRequest;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.service.MemberService;
import com.behcm.domain.workout.dto.WorkoutFeedItemResponse;
import com.behcm.domain.workout.enums.PeriodType;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberService memberService;

    private Member member() {
        Member member = Member.builder()
                .email("user@test.com")
                .password("encoded")
                .nickname("user")
                .role(MemberRole.USER)
                .build();
        try {
            Field field = Member.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(member, 1L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return member;
    }

    @Test
    @DisplayName("getMyInfo는 인증 없이 요청하면 401을 반환한다")
    void getMyInfo_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("getMyInfo는 인증된 사용자의 정보를 반환한다")
    void getMyInfo_authenticated_returnsMemberInfo() throws Exception {
        mockMvc.perform(get("/api/members/me").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.email", is("user@test.com")))
                .andExpect(jsonPath("$.data.nickname", is("user")));
    }

    @Test
    @DisplayName("getMemberProfile은 서비스가 만든 프로필을 그대로 반환한다")
    void getMemberProfile_returnsProfileFromService() throws Exception {
        MemberProfileResponse profile = MemberProfileResponse.builder()
                .id(1L)
                .nickname("user")
                .email("user@test.com")
                .totalWorkoutDays(5)
                .currentStreak(2)
                .longestStreak(3)
                .totalPenalty(0L)
                .role(MemberRole.USER)
                .build();
        given(memberService.getMemberProfile(any(Member.class))).willReturn(profile);

        mockMvc.perform(get("/api/members/profile").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreak", is(2)))
                .andExpect(jsonPath("$.data.longestStreak", is(3)));
    }

    @Test
    @DisplayName("uploadProfileImage는 업로드된 이미지 URL을 반환한다")
    void uploadProfileImage_returnsUploadedUrl() throws Exception {
        given(memberService.uploadProfileImage(any(Member.class), any()))
                .willReturn(ProfileImageUploadResponse.of("https://s3/profile.png"));
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/members/profile/image").file(image).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileUrl", is("https://s3/profile.png")));
    }

    @Test
    @DisplayName("updateMemberProfile은 닉네임이 2자 미만이면 400을 반환한다")
    void updateMemberProfile_nicknameTooShort_returnsBadRequest() throws Exception {
        UpdateMemberProfileRequestJson request = new UpdateMemberProfileRequestJson("a", null, null);

        mockMvc.perform(put("/api/members/profile")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(memberService, org.mockito.Mockito.never())
                .updateMemberProfile(any(Member.class), any(UpdateMemberProfileRequest.class));
    }

    @Test
    @DisplayName("updateMemberProfile은 유효한 요청이면 갱신된 프로필을 반환한다")
    void updateMemberProfile_validRequest_returnsUpdatedProfile() throws Exception {
        UpdateMemberProfileRequestJson request = new UpdateMemberProfileRequestJson("newNick", "bio", null);
        MemberProfileResponse profile = MemberProfileResponse.builder()
                .id(1L)
                .nickname("newNick")
                .email("user@test.com")
                .role(MemberRole.USER)
                .build();
        given(memberService.updateMemberProfile(any(Member.class), any(UpdateMemberProfileRequest.class)))
                .willReturn(profile);

        mockMvc.perform(put("/api/members/profile")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname", is("newNick")));
    }

    @Test
    @DisplayName("getMemberWorkoutFeed는 periodType 문자열을 파싱해 서비스에 전달한다")
    void getMemberWorkoutFeed_parsesPeriodTypeAndDelegates() throws Exception {
        WorkoutFeedItemResponse item = WorkoutFeedItemResponse.builder()
                .id(1L)
                .duration(30)
                .roomName("Room 1")
                .build();
        Page<WorkoutFeedItemResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        given(memberService.getMemberWorkoutFeed(any(Member.class), eq(0), eq(20), eq(PeriodType.WEEK)))
                .willReturn(page);

        mockMvc.perform(get("/api/members/workout-feed")
                        .param("periodType", "WEEK")
                        .with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].roomName", is("Room 1")));
    }

    @Test
    @DisplayName("updateMemberSettings는 갱신된 설정을 반환한다")
    void updateMemberSettings_returnsUpdatedSettings() throws Exception {
        UpdateMemberSettingsRequest request = new UpdateMemberSettingsRequest();
        MemberSettingsResponse response = MemberSettingsResponse.builder()
                .notifications(MemberSettingsResponse.NotificationSettings.builder()
                        .workoutReminder(false).penaltyAlert(true).roomUpdates(true).weeklyReport(true).build())
                .privacy(MemberSettingsResponse.PrivacySettings.builder()
                        .showProfile(true).showWorkouts(true).showStats(true).build())
                .build();
        given(memberService.updateMemberSettings(any(Member.class), any(UpdateMemberSettingsRequest.class)))
                .willReturn(response);

        mockMvc.perform(put("/api/members/settings")
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.workoutReminder", is(false)));
    }

    // UpdateMemberProfileRequest에는 public 생성자가 없어(NoArgsConstructor만 존재) 테스트에서 JSON 직렬화용으로만 사용
    private record UpdateMemberProfileRequestJson(String nickname, String bio, String profileUrl) {
    }
}
