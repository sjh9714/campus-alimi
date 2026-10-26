package com.campusalimi.benefit;

import com.campusalimi.domain.Benefit;
import com.campusalimi.domain.BenefitRepository;
import com.campusalimi.domain.BenefitSeed;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 앱이 뜰 때 benefits.yml을 benefit 표에 맞춘다. 파일에서 빠진 항목은 지우지 않고 비활성으로 둔다. */
@Component
@Order(0)
public class BenefitSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenefitSeeder.class);

    private final BenefitRepository benefits;

    public BenefitSeeder(BenefitRepository benefits) {
        this.benefits = benefits;
    }

    public record Result(int total, int inserted, int updated, int deactivated) {
    }

    @Override
    public void run(ApplicationArguments args) {
        Result r = seed(BenefitSeedFile.load());
        log.info("benefits total={} inserted={} updated={} deactivated={}", r.total(), r.inserted(), r.updated(), r.deactivated());
    }

    @Transactional
    public Result seed(List<BenefitSeed> seeds) {
        int inserted = 0;
        int updated = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (BenefitSeed seed : seeds) {
            seen.add(seed.slug());
            Benefit benefit = benefits.findBySlug(seed.slug()).orElse(null);
            if (benefit == null) {
                benefit = new Benefit(seed.slug());
                benefit.apply(seed);
                benefits.save(benefit);
                inserted++;
            } else if (benefit.apply(seed)) {
                updated++;
            }
        }
        int deactivated = 0;
        for (Benefit b : benefits.findAll()) {
            if (!seen.contains(b.getSlug()) && b.isActive()) {
                b.apply(new BenefitSeed(b.getSlug(), b.getName(), b.getCategory(), b.getSummary(), b.getCondition(),
                        b.getHowTo(), b.getPrice(), b.getSourceName(), b.getSourceUrl(), b.getVerifiedOn(),
                        b.getRenewalRule(), b.getEndsOn(), false));
                deactivated++;
            }
        }
        return new Result(seeds.size(), inserted, updated, deactivated);
    }
}
