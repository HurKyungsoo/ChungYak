package com.portfolio.chungyak.web.form;

import com.portfolio.chungyak.rule.GeneralSupplyInput;

/**
 * 일반공급 가점 계산 입력 폼.
 *
 * 하는 일은 "폼 필드 → GeneralSupplyInput" 변환뿐. 계산·판단은 여기 없다.
 * (Thymeleaf 바인딩용 setter 는 프로젝트 규칙상 Lombok 없이 직접 둔다.)
 */
public class GeneralSupplyForm {

    private Integer age;
    private boolean married;
    private Integer houselessMonths;
    private Integer dependents;
    private Integer accountMonths;

    public GeneralSupplyInput toInput() {
        return new GeneralSupplyInput(age, married, houselessMonths, dependents, accountMonths);
    }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public boolean isMarried() { return married; }
    public void setMarried(boolean married) { this.married = married; }

    public Integer getHouselessMonths() { return houselessMonths; }
    public void setHouselessMonths(Integer houselessMonths) { this.houselessMonths = houselessMonths; }

    public Integer getDependents() { return dependents; }
    public void setDependents(Integer dependents) { this.dependents = dependents; }

    public Integer getAccountMonths() { return accountMonths; }
    public void setAccountMonths(Integer accountMonths) { this.accountMonths = accountMonths; }
}
