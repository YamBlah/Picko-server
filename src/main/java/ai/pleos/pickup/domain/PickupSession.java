package ai.pleos.pickup.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pickup_session", indexes = {
        @Index(name = "idx_pickup_session_token", columnList = "token", unique = true)
})
public class PickupSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 웹 링크에 쓰는 짧은 URL-safe 토큰 */
    @Column(nullable = false, unique = true, length = 32)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status;

    // 픽업 위치 (만남 지점)
    @Column(name = "pickup_lat", nullable = false) private double pickupLat;
    @Column(name = "pickup_lng", nullable = false) private double pickupLng;
    @Column(name = "pickup_name", length = 128)    private String pickupName;

    // 픽업 지점 추가 정보 (추천 결과를 그대로 저장)
    @Column(name = "pickup_category", length = 32)
    private String pickupCategory;                 // 편의점 | 카페 | 지하철역 | 은행 | 경로 | null

    @Column(name = "walk_from_destination_m")
    private Double walkFromDestinationM;           // 목적지 → 픽업지점 보행거리 (m)

    @Column(name = "walk_from_destination_min")
    private Double walkFromDestinationMin;         // 위의 분 환산

    @Column(name = "vehicle_distance_m")
    private Double vehicleDistanceM;               // 차량 이동 거리 (m)

    @Column(name = "vehicle_eta_min")
    private Double vehicleEtaMin;                  // 차량 도착 예상 (분)

    // 차량 정보 (보행자 UI 표시용)
    @Column(name = "vehicle_model", length = 64)  private String vehicleModel;
    @Column(name = "vehicle_plate", length = 32)  private String vehiclePlate;
    @Column(name = "vehicle_color", length = 32)  private String vehicleColor;

    // 탑승자 연락처 (SMS 발송용, 선택)
    @Column(name = "passenger_phone", length = 20)
    private String passengerPhone;

    // ===== 매직 링크 (토큰 → 쿠키 교환) =====
    /** 토큰 만료 시각. 지나면 redeem 거부 */
    @Column(name = "token_expires_at", nullable = false)
    private Instant tokenExpiresAt;

    /** 토큰이 교환된 시각. 재사용 방지 */
    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    /** redeem 성공 시 발급되는 쿠키 값 (32바이트 base64url). 이후 모든 통신은 이걸로 인증 */
    @Column(name = "web_session_id", unique = true, length = 64)
    private String webSessionId;

    /** 쿠키 만료 시각 */
    @Column(name = "web_session_expires_at")
    private Instant webSessionExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    protected PickupSession() {}

    public PickupSession(String token, Instant tokenExpiresAt,
                         double pickupLat, double pickupLng, String pickupName,
                         String pickupCategory,
                         Double walkFromDestinationM, Double walkFromDestinationMin,
                         Double vehicleDistanceM, Double vehicleEtaMin,
                         String vehicleModel, String vehiclePlate, String vehicleColor,
                         String passengerPhone) {
        this.token = token;
        this.tokenExpiresAt = tokenExpiresAt;
        this.status = SessionStatus.CREATED;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.pickupName = pickupName;
        this.pickupCategory = pickupCategory;
        this.walkFromDestinationM = walkFromDestinationM;
        this.walkFromDestinationMin = walkFromDestinationMin;
        this.vehicleDistanceM = vehicleDistanceM;
        this.vehicleEtaMin = vehicleEtaMin;
        this.vehicleModel = vehicleModel;
        this.vehiclePlate = vehiclePlate;
        this.vehicleColor = vehicleColor;
        this.passengerPhone = passengerPhone;
    }

    /** 토큰 교환 성공 — 쿠키 값과 만료 시각 기록, 토큰은 1회성으로 소비됨 */
    public void redeem(String webSessionId, Instant webSessionExpiresAt) {
        this.redeemedAt = Instant.now();
        this.webSessionId = webSessionId;
        this.webSessionExpiresAt = webSessionExpiresAt;
    }

    public boolean isTokenExpired(Instant now)      { return now.isAfter(tokenExpiresAt); }
    public boolean isRedeemed()                     { return redeemedAt != null; }
    public boolean isWebSessionValid(Instant now)   {
        return webSessionExpiresAt != null && now.isBefore(webSessionExpiresAt);
    }

    public void markPedestrianJoined() { this.status = SessionStatus.PEDESTRIAN_JOINED; }
    public void markEnRoute()          { this.status = SessionStatus.EN_ROUTE; }
    public void markArrived()          { this.status = SessionStatus.ARRIVED; }
    public void markCompleted()        { this.status = SessionStatus.COMPLETED; }
    public void cancel()               { this.status = SessionStatus.CANCELED; }

    public UUID getId()                   { return id; }
    public String getToken()              { return token; }
    public SessionStatus getStatus()      { return status; }
    public double getPickupLat()          { return pickupLat; }
    public double getPickupLng()          { return pickupLng; }
    public String getPickupName()         { return pickupName; }
    public String getVehicleModel()       { return vehicleModel; }
    public String getVehiclePlate()       { return vehiclePlate; }
    public String getVehicleColor()       { return vehicleColor; }
    public String getPassengerPhone()     { return passengerPhone; }
    public String getPickupCategory()     { return pickupCategory; }
    public Double getWalkFromDestinationM()   { return walkFromDestinationM; }
    public Double getWalkFromDestinationMin() { return walkFromDestinationMin; }
    public Double getVehicleDistanceM()   { return vehicleDistanceM; }
    public Double getVehicleEtaMin()      { return vehicleEtaMin; }
    public Instant getTokenExpiresAt()    { return tokenExpiresAt; }
    public Instant getRedeemedAt()        { return redeemedAt; }
    public String getWebSessionId()       { return webSessionId; }
    public Instant getWebSessionExpiresAt() { return webSessionExpiresAt; }
    public Instant getCreatedAt()         { return createdAt; }
    public Instant getUpdatedAt()         { return updatedAt; }
}
