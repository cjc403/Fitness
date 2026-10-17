package com.gym.fitness.module.agent.task;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentRequestTargetTest {
 @Test void aFrozenCancelReasonIsDecodedAndBoundSeparatelyFromTheRoute(){var p=AgentRequestTarget.parse("PUT","/api/v1/admin/bookings/7/cancel?reason=%E5%90%8E%E5%8F%B0%E5%8F%96%E6%B6%88");assertEquals("/api/v1/admin/bookings/7/cancel",p.path());assertEquals("后台取消",p.query().path("reason").asText());}
 @Test void unknownDuplicatedOrForeignQueriesAreRejected(){for(String p:new String[]{"/api/v1/admin/bookings/7/cancel?reason=x&reason=y","/api/v1/admin/bookings/7/cancel?venueId=2","/api/v1/admin/courses/1?reason=x","https://elsewhere.invalid/api/v1/admin/bookings/7/cancel?reason=x"})assertThrows(RuntimeException.class,()->AgentRequestTarget.parse("PUT",p));}
 @Test void unencodedRouteAndEncodedReasonCannotChangeTheTarget(){assertThrows(RuntimeException.class,()->AgentRequestTarget.parse("PUT","/api/v1/admin/bookings%2f7/cancel?reason=x"));assertNotEquals(AgentRequestTarget.parse("PUT","/api/v1/admin/bookings/7/cancel?reason=x").query(),AgentRequestTarget.parse("PUT","/api/v1/admin/bookings/7/cancel?reason=y").query());}
}
