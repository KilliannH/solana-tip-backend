package com.solanatip.dto;

import lombok.*;

public class AlertSettingsDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {
        private String alertColor;
        private String alertAnimation;
        private String alertSound;
        private Integer alertDuration;
        private String alertImageUrl;
        private boolean isPro;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String alertColor;
        private String alertAnimation;
        private String alertSound;
        private Integer alertDuration;
        private String alertImageUrl;
    }
}
