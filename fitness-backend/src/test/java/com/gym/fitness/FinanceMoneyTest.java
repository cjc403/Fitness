package com.gym.fitness;
import com.gym.fitness.module.finance.service.*;
import com.gym.fitness.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class FinanceMoneyTest {
    @Test void centsRejectsRoundingAndOverflow(){assertEquals(999999999999L,Money.cents(new BigDecimal("9999999999.99")));for(String value:new String[]{"-0.01","0.001","10000000000.00"})assertThrows(BusinessException.class,()->Money.cents(new BigDecimal(value)));}
    @Test void threeUsesAllocateAllTenYuan(){assertEquals(333,Money.consume(1000,3));assertEquals(334,Money.consume(667,2));assertEquals(333,Money.consume(333,1));}
    @Test void proportionalAllocationUsesLargestRemainderAndOrderIdTieBreak(){assertEquals(Map.of(1L,1L,2L,0L,3L,1L),FundingAllocator.allocate(2,Map.of(1L,1L,2L,1L,3L,2L)));assertEquals(Map.of(1L,250L,2L,750L),FundingAllocator.allocate(1000,Map.of(1L,1000L,2L,3000L)));assertEquals(Map.of(1L,499999999999L,2L,500000000000L),FundingAllocator.allocate(999999999999L,Map.of(1L,999999999998L,2L,999999999999L)));}
}
