package com.behcm.domain.admin.member.service;

import com.behcm.domain.admin.member.dto.AdminMemberResponse;
import com.behcm.domain.chat.repository.ChatMessageRepository;
import com.behcm.domain.member.entity.Member;
import com.behcm.domain.member.entity.MemberRole;
import com.behcm.domain.member.repository.MemberRepository;
import com.behcm.domain.member.repository.MemberSettingsRepository;
import com.behcm.domain.notification.repository.FcmTokenRepository;
import com.behcm.domain.penalty.repository.PenaltyRepository;
import com.behcm.domain.rest.repository.RestRepository;
import com.behcm.domain.workout.entity.WorkoutRoom;
import com.behcm.domain.workout.entity.WorkoutRoomMember;
import com.behcm.domain.workout.repository.WorkoutLikeRepository;
import com.behcm.domain.workout.repository.WorkoutRecordRepository;
import com.behcm.domain.workout.repository.WorkoutRoomMemberRepository;
import com.behcm.domain.workout.repository.WorkoutRoomRepository;
import com.behcm.global.exception.CustomException;
import com.behcm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final WorkoutRoomMemberRepository workoutRoomMemberRepository;
    private final WorkoutRecordRepository workoutRecordRepository;
    private final WorkoutLikeRepository workoutLikeRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final MemberSettingsRepository memberSettingsRepository;
    private final WorkoutRoomRepository workoutRoomRepository;
    private final PenaltyRepository penaltyRepository;
    private final RestRepository restRepository;

    public Page<AdminMemberResponse> getMembers(String query, MemberRole role, Pageable pageable) {
        String normalizedQuery = (query != null && !query.isBlank()) ? query : null;
        Page<Member> members = memberRepository.searchAdminMembers(normalizedQuery, role, pageable);
        return members.map(AdminMemberResponse::from);
    }

    @Transactional
    public AdminMemberResponse updateMemberRole(Long memberId, MemberRole newRole) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        member.changeRole(newRole);
        Member saved = memberRepository.save(member);

        return AdminMemberResponse.from(saved);
    }

    @Transactional
    public void deleteMember(Long memberId, Member currentAdmin) {
        // 삭제 대상 회원 조회
        Member memberToDelete = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // 본인 계정 삭제 방지
        if (currentAdmin.getId().equals(memberToDelete.getId())) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "본인 계정은 삭제할 수 없습니다.");
        }

        // 마지막 ADMIN 계정 삭제 방지
        if (memberToDelete.getRole() == MemberRole.ADMIN) {
            long adminCount = memberRepository.countByRole(MemberRole.ADMIN);
            if (adminCount <= 1) {
                throw new CustomException(ErrorCode.INVALID_INPUT, "마지막 ADMIN 계정은 삭제할 수 없습니다.");
            }
        }

        // 회원이 소유한 운동방 처리 (운동방 삭제)
        List<WorkoutRoom> ownedRooms = workoutRoomRepository.findByOwner(memberToDelete);
        for (WorkoutRoom room : ownedRooms) {
            // 운동방의 모든 관련 데이터 삭제
            deleteWorkoutRoomData(room);
            workoutRoomRepository.delete(room);
        }

        // 회원이 참여한 운동방 멤버십 조회 및 관련 데이터 삭제
        List<WorkoutRoomMember> workoutRoomMembers = workoutRoomMemberRepository.findWorkoutRoomMembersByMember(memberToDelete);
        for (WorkoutRoomMember wrm : workoutRoomMembers) {
            // Penalty 삭제
            List<com.behcm.domain.penalty.entity.Penalty> penalties = penaltyRepository.findAllByWorkoutRoomId(wrm.getWorkoutRoom().getId());
            penaltyRepository.deleteAll(penalties);

            // Rest 삭제
            List<com.behcm.domain.rest.entity.Rest> rests = restRepository.findAllByWorkoutRoomMember(wrm);
            restRepository.deleteAll(rests);
        }

        // WorkoutRoomMember 삭제
        workoutRoomMemberRepository.deleteAll(workoutRoomMembers);

        // WorkoutRecord 삭제
        workoutRecordRepository.deleteByMember(memberToDelete);

        // WorkoutLike 삭제
        workoutLikeRepository.deleteByMember(memberToDelete);

        // ChatMessage 삭제
        chatMessageRepository.deleteBySender(memberToDelete);

        // FcmToken 삭제
        fcmTokenRepository.deleteByMember(memberToDelete);

        // MemberSettings 삭제
        memberSettingsRepository.findByMemberId(memberId)
                .ifPresent(memberSettingsRepository::delete);

        // 회원 삭제
        memberRepository.delete(memberToDelete);
    }

    private void deleteWorkoutRoomData(WorkoutRoom workoutRoom) {
        // WorkoutRoomMember의 Rest 삭제
        List<WorkoutRoomMember> members = workoutRoomMemberRepository.findByWorkoutRoomOrderByJoinedAt(workoutRoom);
        for (WorkoutRoomMember wrm : members) {
            List<com.behcm.domain.rest.entity.Rest> rests = restRepository.findAllByWorkoutRoomMember(wrm);
            restRepository.deleteAll(rests);
        }

        // WorkoutLike 삭제 (운동방의 운동 기록에 대한 좋아요 - WorkoutRecord 삭제 전에 처리)
        List<com.behcm.domain.workout.entity.WorkoutRecord> workoutRecords = workoutRecordRepository.findAllByWorkoutRoom(workoutRoom);
        for (com.behcm.domain.workout.entity.WorkoutRecord wr : workoutRecords) {
            workoutLikeRepository.deleteByWorkoutRecord(wr);
        }

        // WorkoutRecord 삭제
        workoutRecordRepository.deleteByWorkoutRoom(workoutRoom);

        // ChatMessage 삭제
        chatMessageRepository.deleteByWorkoutRoom(workoutRoom);

        // Penalty 삭제
        List<com.behcm.domain.penalty.entity.Penalty> penalties = penaltyRepository.findAllByWorkoutRoomId(workoutRoom.getId());
        penaltyRepository.deleteAll(penalties);
    }
}

