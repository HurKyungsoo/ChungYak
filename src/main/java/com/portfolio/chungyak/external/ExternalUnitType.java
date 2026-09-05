package com.portfolio.chungyak.external;

import com.portfolio.chungyak.domain.SupplyBreakdown;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class ExternalUnitType {
    private String modelNo;
    private String typeName;
    private String supplyArea;
    private int generalSupplyCount;
    private int specialSupplyCount;
    private SupplyBreakdown supplyBreakdown;
    private Integer topAmount;
}
