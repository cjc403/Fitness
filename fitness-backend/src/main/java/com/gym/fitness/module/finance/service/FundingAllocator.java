package com.gym.fitness.module.finance.service;
import java.math.BigInteger;
import java.util.*;
/** Largest remainder allocation. Keys are immutable source order IDs. */
public final class FundingAllocator {
    private FundingAllocator(){}
    public static Map<Long,Long> allocate(long amount,Map<Long,Long> balances){
        long total=0;for(long value:balances.values()){if(value<0)throw new IllegalArgumentException("负资金余额");total=Math.addExact(total,value);}
        if(amount<0||amount>total)throw new IllegalArgumentException("分摊超过余额");
        var result=new TreeMap<Long,Long>();if(total==0){balances.keySet().forEach(id->result.put(id,0L));return result;}
        var remainders=new HashMap<Long,BigInteger>();long allocated=0;
        for(var entry:balances.entrySet()){var parts=BigInteger.valueOf(amount).multiply(BigInteger.valueOf(entry.getValue())).divideAndRemainder(BigInteger.valueOf(total));long value=parts[0].longValueExact();result.put(entry.getKey(),value);remainders.put(entry.getKey(),parts[1]);allocated+=value;}
        var ids=new ArrayList<>(balances.keySet());ids.sort(Comparator.<Long,BigInteger>comparing(remainders::get).reversed().thenComparingLong(Long::longValue));
        for(int i=0;i<amount-allocated;i++)result.compute(ids.get(i),(id,value)->value+1);
        return result;
    }
}
