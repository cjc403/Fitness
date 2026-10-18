package com.gym.fitness.module.agent.task;

import com.fasterxml.jackson.databind.*;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.exception.BusinessException;
import com.gym.fitness.module.agent.computer.ComputerAgentService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Runs only for a confirmed V2 page write; ordinary requests retain their existing behavior. */
@Component @RequiredArgsConstructor
public class AgentMutationFilter extends OncePerRequestFilter {
    private final AgentMutationReceiptService receipts;private final ComputerAgentService computer;private final ObjectMapper json;
    private final com.gym.fitness.config.AiProperties aiProperties;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException{
        String permit=request.getHeader("X-FitChain-Agent-Permit");
        if(permit==null){chain.doFilter(request,response);return;}
        var cached=new ContentCachingResponseWrapper(response);
        try{
            if(!aiProperties.isTaskV2Enabled())throw com.gym.fitness.module.agent.AgentIdentity.invalid("持续网页Agent已停用，旧提交许可不会放行，请刷新核对");
            new AgentSkillRollout(aiProperties).requireMutation(request.getRequestURI());
            byte[] bytes=request.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("请求过大");
            JsonNode body=bytes.length==0?json.nullNode():json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(bytes);
            String path=request.getRequestURI()+(request.getQueryString()==null?"":"?"+request.getQueryString());
            var me=computer.session();
            receipts.execute(me,permit,request.getMethod(),path,body,()->{
                try{chain.doFilter(new ReplayRequest(request,bytes),cached);byte[] result=cached.getContentAsByteArray();if(result.length>262144)throw new IllegalStateException("业务回执过大");return json.readTree(result);}
                catch(IOException|ServletException e){throw new IllegalStateException("业务请求未完成",e);}
            });
            cached.setHeader("X-FitChain-Agent-Receipt",permit);cached.copyBodyToResponse();
        }catch(RuntimeException failure){
            cached.resetBuffer();cached.setStatus(failure instanceof BusinessException b&&b.getErrorCode()==ErrorCode.FORBIDDEN?403:400);
            cached.setCharacterEncoding("UTF-8");cached.setContentType("application/json;charset=UTF-8");
            String message=failure instanceof BusinessException?failure.getMessage():"操作未完成，已回滚。请核对提交记录后重新准备。";
            json.writeValue(cached.getWriter(),Result.error(failure instanceof BusinessException b?b.getErrorCode():ErrorCode.OPERATION_NOT_ALLOWED,message));cached.copyBodyToResponse();
        }
    }
    private static final class ReplayRequest extends HttpServletRequestWrapper{
        private final byte[] body;ReplayRequest(HttpServletRequest r,byte[] body){super(r);this.body=body;}
        @Override public ServletInputStream getInputStream(){var input=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("异步提交不受支持");}};}
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
    }
}
