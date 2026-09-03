package com.portfolio.chungyak.rule;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * rule 패키지가 쓰는 외부 설정(소득 기준표 등) 등록.
 */
@Configuration
@EnableConfigurationProperties({
        IncomeReferenceProperties.class,
        SpecialSupplyRequirementProperties.class,
        ReWinRestrictionProperties.class,
        AccountRequirementProperties.class
})
public class RuleConfig {
}
