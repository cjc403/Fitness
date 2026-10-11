package com.gym.fitness;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessTimezoneTest {
    @Test void applicationInitializesShanghaiTimeBeforeHibernateOnAUtcJvm() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var process = new ProcessBuilder(java, "-Duser.timezone=UTC", "-cp", System.getProperty("java.class.path"),
            Probe.class.getName()).redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output.trim()).isEqualTo("Asia/Shanghai|Asia/Shanghai");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            Class.forName("com.gym.fitness.FitnessApplication");
            System.out.println(java.time.ZoneId.systemDefault() + "|" + java.time.Clock.systemDefaultZone().getZone());
        }
    }
}
