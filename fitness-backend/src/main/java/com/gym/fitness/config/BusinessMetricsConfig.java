package com.gym.fitness.config;

import com.gym.fitness.module.booking.repository.BookingRepository;
import com.gym.fitness.module.course.repository.CourseScheduleRepository;
import com.gym.fitness.module.member.repository.MemberRepository;
import com.gym.fitness.module.organization.repository.VenueRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
public class BusinessMetricsConfig {

    @Bean
    public MeterBinder businessMetrics(MemberRepository members,
                                       VenueRepository venues,
                                       CourseScheduleRepository schedules,
                                       BookingRepository bookings) {
        return registry -> register(registry, members, venues, schedules, bookings);
    }

    private void register(MeterRegistry registry, MemberRepository members,
                          VenueRepository venues, CourseScheduleRepository schedules,
                          BookingRepository bookings) {
        Gauge.builder("fitness_members_total", members, MemberRepository::count)
                .description("Total number of fitness members").register(registry);
        Gauge.builder("fitness_venues_total", venues, VenueRepository::count)
                .description("Total number of venues").register(registry);
        Gauge.builder("fitness_bookings_booked", bookings, repository -> repository.countByStatus("BOOKED"))
                .description("Current booked reservations").register(registry);
        Gauge.builder("fitness_bookings_attended", bookings, repository -> repository.countByStatus("ATTENDED"))
                .description("Attended reservations").register(registry);
        Gauge.builder("fitness_schedules_today", schedules, repository -> {
            LocalDate today = LocalDate.now();
            return repository.countByScheduledStartTimeBetween(
                    today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        }).description("Schedules planned for today").register(registry);
    }
}
