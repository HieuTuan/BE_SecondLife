package com.secondlife.secondlife.seeder;

import com.secondlife.secondlife.config.InspectionCenterSeedProperties;
import com.secondlife.secondlife.dto.request.InspectionCenterProfileRequest;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(InspectionCenterSeedProperties.class)
@ConditionalOnProperty(name = "app.seeder.inspection-center-2.enabled", havingValue = "true")
public class InspectionCenterAccountSeeder implements ApplicationRunner {
    private final InspectionCenterSeedProperties settings;
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final InspectionCenterProfileService profiles;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(settings.email())) {
            throw new IllegalStateException("SEED_INSPECTION_CENTER_2_EMAIL is required");
        }

        String email = settings.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        if (!StringUtils.hasText(settings.password())) {
            throw new IllegalStateException("SEED_INSPECTION_CENTER_2_PASSWORD is required");
        }

        Role centerRole = roles.findByCode(RoleCode.INSPECTION_CENTER.name())
                .orElseThrow(() -> new IllegalStateException("INSPECTION_CENTER role is not initialized"));
        User center = new User(email, passwordEncoder.encode(settings.password()), AccountStatus.ACTIVE);
        center.setEmailVerified(true);
        center.addRole(centerRole);
        users.save(center);
        profiles.updateMyProfile(center.getId(), new InspectionCenterProfileRequest(
                settings.fullName(), settings.phone(), settings.province(), null, settings.ward(),
                settings.streetAddress(), settings.latitude(), settings.longitude()));
        log.info("Provisioned inspection center seed account [{}]", email);
    }
}
