package com.campusalimi;

import com.campusalimi.config.AppProperties;
import com.campusalimi.notice.NoticeCollector;
import com.campusalimi.schedule.ScheduleCollector;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * RUN_ONCE=schedule|notice|all 로 띄우면 수집을 한 번 돌리고 종료한다.
 * 운영에서는 비워 두고 스케줄러에 맡긴다.
 */
@Component
@Order(10)
public class RunOnceRunner implements ApplicationRunner {

    private final AppProperties props;
    private final ScheduleCollector schedules;
    private final NoticeCollector noticesCollector;
    private final ConfigurableApplicationContext context;

    public RunOnceRunner(AppProperties props, ScheduleCollector schedules, NoticeCollector noticesCollector,
            ConfigurableApplicationContext context) {
        this.props = props;
        this.schedules = schedules;
        this.noticesCollector = noticesCollector;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        String mode = props.runOnce() == null ? "" : props.runOnce().trim();
        if (mode.isEmpty()) {
            return;
        }
        int exit = 0;
        try {
            if (mode.equals("schedule") || mode.equals("all")) {
                for (int year : props.schedule().years()) {
                    System.out.println("schedule " + year + " " + schedules.collect(year));
                }
            }
            if (mode.equals("notice") || mode.equals("all")) {
                System.out.println("notice " + noticesCollector.collectAll());
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            exit = 1;
        }
        System.exit(SpringApplication.exit(context, () -> 0) + exit);
    }
}
