package com.behcm.support;

import com.behcm.domain.admin.member.service.AdminMemberService;
import com.behcm.domain.admin.tossaccess.service.AdminTossAccessService;
import com.behcm.domain.admin.workout.service.AdminWorkoutRoomService;
import com.behcm.domain.auth.service.AuthService;
import com.behcm.domain.auth.service.EmailVerificationService;
import com.behcm.domain.chat.service.ChatService;
import com.behcm.domain.member.service.MemberService;
import com.behcm.domain.notification.service.NotificationFacade;
import com.behcm.domain.penalty.service.PenaltyService;
import com.behcm.domain.rest.service.RestService;
import com.behcm.domain.social.service.WorkoutCommentService;
import com.behcm.domain.social.service.WorkoutReactionService;
import com.behcm.domain.stats.service.StatsService;
import com.behcm.domain.stock.service.StockService;
import com.behcm.domain.tossstock.service.TossAccessChecker;
import com.behcm.domain.tossstock.service.TossOrderService;
import com.behcm.domain.tossstock.service.TossStockService;
import com.behcm.domain.workout.service.WorkoutRoomService;
import com.behcm.domain.workout.service.WorkoutService;
import com.behcm.global.config.toss.TossInvestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * 스프링 컨텍스트가 필요한 테스트({@code @SpringBootTest})의 공통 부모.
 *
 * <p>스프링은 {@code @MockitoBean} 구성이 조금이라도 다르면 컨텍스트를 새로 띄우고 캐시에 따로 보관한다.
 * 컨트롤러 테스트마다 자기 서비스만 목킹하면 클래스 수만큼(20개) 컨텍스트가 뜨고, 그때마다
 * Flyway 마이그레이션·Hibernate 스키마 검증·커넥션 풀 생성이 반복되어 CI 테스트 시간의 대부분을 차지한다.
 *
 * <p>그래서 목 빈을 모두 여기에 한 번만 선언한다. 모든 하위 클래스가 같은 구성을 공유하므로 컨텍스트는
 * 테스트 JVM 전체에서 <b>한 번</b>만 뜬다. 목은 {@code MockReset.AFTER}(기본값)로 테스트마다 초기화되므로
 * 클래스 사이에 stubbing 이 새지 않는다.
 *
 * <p>새 컨트롤러 테스트를 추가할 때는 이 클래스를 상속하고, 필요한 서비스 목이 없으면 여기에 추가한다.
 * 하위 클래스에서 {@code @MockitoBean}/{@code @MockitoSpyBean}/{@code @TestPropertySource} 등을
 * 따로 선언하면 다시 별도 컨텍스트가 생기므로 피한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected AdminMemberService adminMemberService;

    @MockitoBean
    protected AdminTossAccessService adminTossAccessService;

    @MockitoBean
    protected AdminWorkoutRoomService adminWorkoutRoomService;

    @MockitoBean
    protected AuthService authService;

    @MockitoBean
    protected EmailVerificationService emailVerificationService;

    @MockitoBean
    protected ChatService chatService;

    @MockitoBean
    protected MemberService memberService;

    @MockitoBean
    protected NotificationFacade notificationFacade;

    @MockitoBean
    protected PenaltyService penaltyService;

    @MockitoBean
    protected RestService restService;

    @MockitoBean
    protected WorkoutReactionService workoutReactionService;

    @MockitoBean
    protected WorkoutCommentService workoutCommentService;

    @MockitoBean
    protected StatsService statsService;

    @MockitoBean
    protected StockService stockService;

    @MockitoBean
    protected TossAccessChecker tossAccessChecker;

    @MockitoBean
    protected TossOrderService tossOrderService;

    @MockitoBean
    protected TossStockService tossStockService;

    @MockitoBean
    protected TossInvestClient tossInvestClient;

    @MockitoBean
    protected WorkoutService workoutService;

    @MockitoBean
    protected WorkoutRoomService workoutRoomService;
}
