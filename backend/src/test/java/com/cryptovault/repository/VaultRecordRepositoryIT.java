package com.cryptovault.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptovault.entity.CryptoKey;
import com.cryptovault.entity.Role;
import com.cryptovault.entity.User;
import com.cryptovault.entity.VaultRecord;
import com.cryptovault.entity.KeyStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Containerized Integration Test verifying VaultRecordRepository operations
 * against actual running instances of MySQL and Redis.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class VaultRecordRepositoryIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("cryptovault_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.url", mysql::getJdbcUrl);
        registry.add("spring.flyway.user", mysql::getUsername);
        registry.add("spring.flyway.password", mysql::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        
        // Suppress developer test console during integration tests
        registry.add("cryptovault.dev.console-enabled", () -> "false");
    }

    @Autowired
    private VaultRecordRepository vaultRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CryptoKeyRepository keyRepository;

    @Test
    void testActiveRecordsLifecycleAgainstRealContainers() {
        // 1. Create and save a user role and user
        Role role = Role.builder()
                .name("USER")
                .build();
        Role savedRole = roleRepository.save(role);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test.integration@bank.com")
                .passwordHash("hashedpassword")
                .role(savedRole)
                .isActive(true)
                .build();
        User savedUser = userRepository.save(user);

        // 2. Create and save a key version
        CryptoKey cryptoKey = CryptoKey.builder()
                .version(1)
                .algorithm("AES")
                .status(KeyStatus.ACTIVE)
                .encryptedKey("encryptedkeymaterial".getBytes(StandardCharsets.UTF_8))
                .build();
        CryptoKey savedKey = keyRepository.save(cryptoKey);

        // 3. Store active vault records
        VaultRecord record1 = VaultRecord.builder()
                .name("secret-one")
                .encryptedData("data-one".getBytes(StandardCharsets.UTF_8))
                .algorithmUsed("AES")
                .cryptoKey(savedKey)
                .user(savedUser)
                .build();

        VaultRecord record2 = VaultRecord.builder()
                .name("secret-two")
                .encryptedData("data-two".getBytes(StandardCharsets.UTF_8))
                .algorithmUsed("AES")
                .cryptoKey(savedKey)
                .user(savedUser)
                .build();

        VaultRecord saved1 = vaultRepository.save(record1);
        VaultRecord saved2 = vaultRepository.save(record2);

        // 4. Retrieve active records
        List<VaultRecord> activeList = vaultRepository.findActiveByUserId(savedUser.getId());
        assertThat(activeList).hasSize(2);

        // 5. Soft delete one record and verify it is omitted from active lookups
        saved1.setDeletedAt(LocalDateTime.now());
        vaultRepository.save(saved1);

        Optional<VaultRecord> deletedLookup = vaultRepository.findActiveById(saved1.getId());
        assertThat(deletedLookup).isNotPresent();

        List<VaultRecord> activeListAfterDelete = vaultRepository.findActiveByUserId(savedUser.getId());
        assertThat(activeListAfterDelete).hasSize(1);
        assertThat(activeListAfterDelete.get(0).getName()).isEqualTo("secret-two");
    }
}
