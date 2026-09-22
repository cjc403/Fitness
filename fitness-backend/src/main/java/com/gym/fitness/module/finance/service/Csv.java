package com.gym.fitness.module.finance.service;
final class Csv {
    private Csv() {}
    static String cell(String s) {
        if(s==null)s="";
        if(!s.isEmpty()&&(s.charAt(0)<=32||"=+-@".indexOf(s.charAt(0))>=0))s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
}
