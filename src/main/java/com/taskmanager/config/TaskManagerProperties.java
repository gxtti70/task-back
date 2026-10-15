package com.taskmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter @Setter
@ConfigurationProperties(prefix = "taskmanager")
public class TaskManagerProperties {
    private Map<String, Plan> plans = new HashMap<>();
    private Billing billing = new Billing();
    private Storage storage = new Storage();
    @Getter @Setter public static class Plan {
        private int maxProjects;
        private int maxMembersPerProject;
        private BigDecimal price = BigDecimal.ZERO;
        private String currency = "USD";
        private String stripePriceId;
        private List<String> features = new ArrayList<>();
        private Map<String, Boolean> capabilities = new HashMap<>();
        private int maxAttachmentMB;
        private int maxAttachmentsPerTask;
    }
    @Getter @Setter public static class Billing {
        private boolean simulate = true;
        private String stripeSecretKey;
        private String stripeWebhookSecret;
        private String frontendUrl = "http://localhost:4200";
    }
    @Getter @Setter public static class Storage {
        private String localDirectory = "./data/attachments";
        private S3 s3 = new S3();
    }
    @Getter @Setter public static class S3 {
        private String region = "us-east-1";
        private String bucket = "";
    }
}
