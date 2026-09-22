package com.gym.fitness.module.finance.service;
import java.math.*;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
public final class Money {
    private Money(){}
    public static final long MAX_CENTS=999999999999L;
    public static long cents(BigDecimal amount){
        try{if(amount==null||amount.signum()<0)throw new ArithmeticException();long value=amount.movePointRight(2).longValueExact();if(value>MAX_CENTS)throw new ArithmeticException();return value;}
        catch(ArithmeticException e){throw new BusinessException(ErrorCode.PARAM_ERROR,"金额必须为非负、最多两位小数且不超过9999999999.99元");}
    }
    public static BigDecimal yuan(long cents){return BigDecimal.valueOf(cents,2);}
    public static long consume(long balance,int remaining){if(balance<0||remaining<=0)throw new IllegalArgumentException("无剩余次数");return remaining==1?balance:BigDecimal.valueOf(balance).divide(BigDecimal.valueOf(remaining),0,RoundingMode.HALF_UP).longValueExact();}
    public static long add(long left,long right){long result=Math.addExact(left,right);if(result<0||result>MAX_CENTS)throw new BusinessException(ErrorCode.PARAM_ERROR,"权益金额超限");return result;}
}
