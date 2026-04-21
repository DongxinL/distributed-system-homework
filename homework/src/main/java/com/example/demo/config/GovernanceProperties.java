package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@RefreshScope
@ConfigurationProperties(prefix = "homework.governance")
public class GovernanceProperties {

    private String banner;

    private String tip;

    private long unstableDelayMs;

    private String fallbackMessage;

    public String getBanner() {
        return banner;
    }

    public void setBanner(String banner) {
        this.banner = banner;
    }

    public String getTip() {
        return tip;
    }

    public void setTip(String tip) {
        this.tip = tip;
    }

    public long getUnstableDelayMs() {
        return unstableDelayMs;
    }

    public void setUnstableDelayMs(long unstableDelayMs) {
        this.unstableDelayMs = unstableDelayMs;
    }

    public String getFallbackMessage() {
        return fallbackMessage;
    }

    public void setFallbackMessage(String fallbackMessage) {
        this.fallbackMessage = fallbackMessage;
    }
}