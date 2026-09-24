package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserCredit;
import com.secondlife.secondlife.repository.TopupPackageRepository;
import com.secondlife.secondlife.repository.UserCreditRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.CreditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CreditServiceImpl implements CreditService {

    private final UserCreditRepository userCreditRepository;
    private final TopupPackageRepository topupPackageRepository;
    private final UserRepository userRepository;

    public CreditServiceImpl(UserCreditRepository userCreditRepository,
                             TopupPackageRepository topupPackageRepository,
                             UserRepository userRepository) {
        this.userCreditRepository = userCreditRepository;
        this.topupPackageRepository = topupPackageRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserCredit getUserCredit(UUID userId) {
        return userCreditRepository.findByUserId(userId)
                .orElseGet(() -> {
                    // Create default credit if not exists
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found"));
                    UserCredit newCredit = new UserCredit(user);
                    return userCreditRepository.save(newCredit);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopupPackage> getAllTopupPackages() {
        return topupPackageRepository.findAll();
    }

    @Override
    @Transactional
    public UserCredit purchaseTopupPackage(UUID userId, UUID packageId) {
        TopupPackage pkg = topupPackageRepository.findById(packageId)
                .orElseThrow(() -> new RuntimeException("Topup package not found"));

        UserCredit userCredit = getUserCredit(userId);
        
        userCredit.setPostCredits(userCredit.getPostCredits() + pkg.getPostCredits());
        userCredit.setChatCredits(userCredit.getChatCredits() + pkg.getChatCredits());
        
        return userCreditRepository.save(userCredit);
    }

    @Override
    @Transactional
    public void deductPostCredit(UUID userId) {
        UserCredit userCredit = getUserCredit(userId);
        if (userCredit.getPostCredits() <= 0) {
            throw new RuntimeException("Insufficient post credits");
        }
        userCredit.setPostCredits(userCredit.getPostCredits() - 1);
        userCreditRepository.save(userCredit);
    }

    @Override
    @Transactional
    public void deductChatCredit(UUID userId) {
        UserCredit userCredit = getUserCredit(userId);
        if (userCredit.getChatCredits() <= 0) {
            throw new RuntimeException("Insufficient chat credits");
        }
        userCredit.setChatCredits(userCredit.getChatCredits() - 1);
        userCreditRepository.save(userCredit);
    }
}
