package com.ecommerce.management.command;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import com.ecommerce.management.EcommerceManagementApplication;
import com.ecommerce.management.service.ExpiredPendingOrderCleanupService;
import com.ecommerce.management.service.ExpiredPendingOrderCleanupService.CleanupResult;
import com.ecommerce.management.service.PaymentStatusReconciler;

public final class ExpiredPendingOrderCleanupCommand {

    private ExpiredPendingOrderCleanupCommand() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(EcommerceManagementApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("cli");
        application.setDefaultProperties(Map.of("spring.devtools.restart.enabled", "false"));

        try (ConfigurableApplicationContext context = application.run(args)) {
            context.getBean(PaymentStatusReconciler.class).reconcile();
            CleanupResult result = context.getBean(ExpiredPendingOrderCleanupService.class).cleanup();
            System.out.printf(
                    "Cleanup completed: %d expired pending order(s) deleted (cutoff: %s).%n",
                    result.deletedOrderCount(), result.cutoff());
        }
    }
}
