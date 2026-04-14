package com.hayden.multiagentide.propagation.service;

import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoAiPropagatorBootstrap {

    private final PropagatorRegistrationService propagatorRegistrationService;

    @Bean
    public ApplicationRunner bootstrapAutoAiPropagators() {
        return args -> seedAutoAiPropagators();
    }

    @Transactional
    public void seedAutoAiPropagators() {
        int upserted = propagatorRegistrationService.ensureAutoAiPropagatorsRegistered();
        log.info("Ensured {} auto AI propagator registrations.", upserted);
    }

    @Transactional
    public void enableAiPropagator(String registrationId) {
        var upserted = propagatorRegistrationService.enableAiPropagator(registrationId);
        log.info("Ensured {} auto AI propagator registrations enabled.", upserted == null ? 0 : 1);
    }

    @Transactional
    public void disableAiPropagator(String registrationId) {
        var upserted = propagatorRegistrationService.disableAiPropagator(registrationId);
        log.info("Ensured {} auto AI propagator registrations disabled.", upserted == null ? 0 : 1);
    }
}
