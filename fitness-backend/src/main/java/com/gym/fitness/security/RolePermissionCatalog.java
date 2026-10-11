package com.gym.fitness.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central role/permission matrix used by Spring Security and returned to the UI.
 * Keeping the matrix in one place prevents the menu and API rules from drifting.
 */
public final class RolePermissionCatalog {

    public static final String DASHBOARD_READ = "DASHBOARD_READ";
    public static final String MEMBER_READ = "MEMBER_READ";
    public static final String MEMBER_WRITE = "MEMBER_WRITE";
    public static final String COURSE_READ = "COURSE_READ";
    public static final String COURSE_WRITE = "COURSE_WRITE";
    public static final String SCHEDULE_WRITE = "SCHEDULE_WRITE";
    public static final String BOOKING_READ = "BOOKING_READ";
    public static final String BOOKING_WRITE = "BOOKING_WRITE";
    public static final String CHECKIN_WRITE = "CHECKIN_WRITE";
    public static final String VENUE_READ = "VENUE_READ";
    public static final String VENUE_CREATE = "VENUE_CREATE";
    public static final String VENUE_WRITE = "VENUE_WRITE";
    public static final String VENUE_SWITCH = "VENUE_SWITCH";
    public static final String ASSISTANT_USE = "ASSISTANT_USE";
    public static final String MEMBER_SELF_SERVICE = "MEMBER_SELF_SERVICE";
    public static final String COACH_WORKBENCH = "COACH_WORKBENCH";
    public static final String ACCOUNT_MANAGE = "ACCOUNT_MANAGE";
    public static final String PRODUCT_READ = "PRODUCT_READ";
    public static final String PRODUCT_WRITE = "PRODUCT_WRITE";
    public static final String SALE_READ = "SALE_READ";
    public static final String SALE_WRITE = "SALE_WRITE";
    public static final String MEMBERSHIP_READ = "MEMBERSHIP_READ";
    public static final String MEMBERSHIP_WRITE = "MEMBERSHIP_WRITE";
    public static final String FINANCE_READ = "FINANCE_READ";
    public static final String REFUND_REQUEST = "REFUND_REQUEST";
    public static final String REFUND_APPROVE = "REFUND_APPROVE";
    public static final String REFUND_PAY = "REFUND_PAY";
    public static final String VALUE_RECONCILE = "VALUE_RECONCILE";
    public static final String VALUE_TRANSFER_VERIFY = "VALUE_TRANSFER_VERIFY";
    public static final String FINANCE_REPORT_READ = "FINANCE_REPORT_READ";
    public static final String ACCESS_WRITE = "ACCESS_WRITE";
    public static final String COACH_PERFORMANCE_READ = "COACH_PERFORMANCE_READ";
    /** 优惠券（营销）：查看券模板与发放记录。 */
    public static final String COUPON_READ = "COUPON_READ";
    /** 优惠券：建券、改券、发放。 */
    public static final String COUPON_WRITE = "COUPON_WRITE";
    /** 课程评价查看（教练看本人、店长看本店）。 */
    public static final String COURSE_REVIEW_READ = "COURSE_REVIEW_READ";
    public static final String LEAD_READ = "LEAD_READ";
    public static final String LEAD_WRITE = "LEAD_WRITE";

    public static final String CASH_SHIFT_READ = "CASH_SHIFT_READ";
    public static final String CASH_SHIFT_WRITE = "CASH_SHIFT_WRITE";
    public static final String CASH_SHIFT_REVIEW = "CASH_SHIFT_REVIEW";

    public static final String TRAINING_PLAN_READ = "TRAINING_PLAN_READ";
    public static final String TRAINING_PLAN_WRITE = "TRAINING_PLAN_WRITE";

    public static final String TRAINING_PLAN_ARCHIVE = "TRAINING_PLAN_ARCHIVE";

    private static final Map<String, String> ROLE_NAMES = Map.of(
            "SUPER_ADMIN", "总部管理员",
            "MANAGER", "店长",
            "COACH", "教练",
            "RECEPTION", "前台",
            "MEMBER", "会员",
            "SALES", "会籍顾问"
    );

    private static final Map<String, List<String>> PERMISSIONS = createPermissions();

    private RolePermissionCatalog() {
    }

    public static List<String> permissionsFor(String role) {
        return PERMISSIONS.getOrDefault(role, List.of());
    }

    public static String roleName(String role) {
        return ROLE_NAMES.getOrDefault(role, role);
    }

    private static Map<String, List<String>> createPermissions() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("SUPER_ADMIN", List.of(
                DASHBOARD_READ, MEMBER_READ, MEMBER_WRITE, COURSE_READ, COURSE_WRITE,
                SCHEDULE_WRITE, BOOKING_READ, BOOKING_WRITE, CHECKIN_WRITE,
                VENUE_READ, VENUE_CREATE, VENUE_WRITE, VENUE_SWITCH, ASSISTANT_USE, ACCOUNT_MANAGE,
                PRODUCT_READ, PRODUCT_WRITE, SALE_READ, SALE_WRITE, MEMBERSHIP_READ, MEMBERSHIP_WRITE,
                FINANCE_READ, REFUND_REQUEST, REFUND_APPROVE, REFUND_PAY, VALUE_RECONCILE, VALUE_TRANSFER_VERIFY, FINANCE_REPORT_READ, ACCESS_WRITE, COACH_PERFORMANCE_READ,
                COUPON_READ, COUPON_WRITE, COURSE_REVIEW_READ, LEAD_READ, LEAD_WRITE, CASH_SHIFT_READ, CASH_SHIFT_WRITE, CASH_SHIFT_REVIEW, TRAINING_PLAN_READ, TRAINING_PLAN_ARCHIVE));
        result.put("MANAGER", List.of(
                DASHBOARD_READ, MEMBER_READ, MEMBER_WRITE, COURSE_READ, COURSE_WRITE,
                SCHEDULE_WRITE, BOOKING_READ, BOOKING_WRITE, CHECKIN_WRITE,
                VENUE_READ, VENUE_WRITE, ASSISTANT_USE, ACCOUNT_MANAGE,
                PRODUCT_READ, PRODUCT_WRITE, SALE_READ, SALE_WRITE, MEMBERSHIP_READ, MEMBERSHIP_WRITE,
                FINANCE_READ, REFUND_REQUEST, REFUND_APPROVE, REFUND_PAY, VALUE_RECONCILE, FINANCE_REPORT_READ, ACCESS_WRITE, COACH_PERFORMANCE_READ,
                COUPON_READ, COUPON_WRITE, COURSE_REVIEW_READ, LEAD_READ, LEAD_WRITE, CASH_SHIFT_READ, CASH_SHIFT_WRITE, CASH_SHIFT_REVIEW, TRAINING_PLAN_READ, TRAINING_PLAN_ARCHIVE));
        // 教练只看自己的评分（/coach/reviews/summary，COACH_WORKBENCH 放行）；
        // 店长视图 /admin/reviews/summary 需要 COURSE_REVIEW_READ，教练不持有
        result.put("COACH", List.of(
                COURSE_READ, BOOKING_READ, CHECKIN_WRITE, COACH_WORKBENCH, ASSISTANT_USE,
                COACH_PERFORMANCE_READ, TRAINING_PLAN_READ, TRAINING_PLAN_WRITE, TRAINING_PLAN_ARCHIVE));
        result.put("RECEPTION", List.of(
                MEMBER_READ, MEMBER_WRITE, COURSE_READ, BOOKING_READ,
                BOOKING_WRITE, CHECKIN_WRITE, ASSISTANT_USE, PRODUCT_READ, SALE_READ, SALE_WRITE,
                MEMBERSHIP_READ, MEMBERSHIP_WRITE, FINANCE_READ, REFUND_REQUEST, FINANCE_REPORT_READ, ACCESS_WRITE,
                COUPON_READ, COUPON_WRITE, COURSE_REVIEW_READ, CASH_SHIFT_READ, CASH_SHIFT_WRITE));
        // 会员不需要 COURSE_READ：会员端课程走 /api/v1/member/*（MEMBER_SELF_SERVICE）；
        // 之前多这一个权限会让管理端菜单把「课程与排课/周课表」露给会员，点进去还因缺少 SCHEDULE_WRITE 弹 403
        result.put("MEMBER", List.of(MEMBER_SELF_SERVICE, ASSISTANT_USE));
        result.put("SALES", List.of(MEMBER_READ, MEMBER_WRITE, ASSISTANT_USE, COUPON_READ, COUPON_WRITE,
                PRODUCT_READ, SALE_READ, SALE_WRITE, MEMBERSHIP_READ, LEAD_READ, LEAD_WRITE));
        return Map.copyOf(result);
    }
}
