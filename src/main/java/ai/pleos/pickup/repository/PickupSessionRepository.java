package ai.pleos.pickup.repository;

import ai.pleos.pickup.domain.PickupSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PickupSessionRepository extends JpaRepository<PickupSession, UUID> {
    Optional<PickupSession> findByToken(String token);
    Optional<PickupSession> findByWebSessionId(String webSessionId);
}
