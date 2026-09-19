package com.contestmate.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;

    public UserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AppUser getOrCreate(String clientId) {
        return appUserRepository.findByClientId(clientId)
                .orElseGet(() -> appUserRepository.save(new AppUser(clientId)));
    }
}
