package ai.pleos.pickup.domain;

public enum SessionStatus {
    CREATED,              // 세션 생성됨, 아직 보행자 미참여
    PEDESTRIAN_JOINED,    // 보행자가 링크 열고 길 안내 시작
    EN_ROUTE,             // 차량 이동 중
    ARRIVED,              // 차량 도착
    COMPLETED,            // 픽업 완료
    CANCELED              // 취소
}
