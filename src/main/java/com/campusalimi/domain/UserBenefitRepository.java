package com.campusalimi.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBenefitRepository extends JpaRepository<UserBenefit, UserBenefit.Key> {

    List<UserBenefit> findByUserId(Long userId);
}
