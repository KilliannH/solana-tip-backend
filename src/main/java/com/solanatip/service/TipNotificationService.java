package com.solanatip.service;

import com.solanatip.entity.Creator;
import com.solanatip.entity.Tip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class TipNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:https://solana-tip.com}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@solana-tip.com}")
    private String fromEmail;

    @Async
    public void sendTipNotification(Creator creator, Tip tip) {
        if (creator.getEmail() == null || creator.getEmail().isBlank()) {
            return;
        }
        if (!creator.isNotifyTipReceived()) {
            log.debug("Tip notification skipped for {} (disabled)", creator.getUsername());
            return;
        }

        String senderName = tip.getSenderDisplayName() != null && !tip.getSenderDisplayName().isBlank()
                ? tip.getSenderDisplayName()
                : shortenWallet(tip.getSenderWalletAddress());

        String subject = "⚡ You received " + tip.getAmountSol().toPlainString() + " SOL from " + senderName;

        String html = buildHtml(creator, tip, senderName);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(creator.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            log.info("Tip notification sent to {} for {} SOL", creator.getEmail(), tip.getAmountSol());
        } catch (Exception e) {
            log.error("Failed to send tip notification to {}: {}", creator.getEmail(), e.getMessage());
        }
    }

    private String buildHtml(Creator creator, Tip tip, String senderName) {
        String profileUrl = baseUrl + "/creator/" + creator.getUsername();
        String explorerUrl = "https://explorer.solana.com/tx/" + tip.getTransactionSignature();
        String message = tip.getMessage() != null && !tip.getMessage().isBlank()
                ? "<div style=\"background:#0d1117;border:1px solid #1a2332;border-radius:12px;padding:16px;margin:16px 0;\">"
                + "<p style=\"color:#8b949e;font-size:13px;margin:0 0 4px;\">Message:</p>"
                + "<p style=\"color:#e6edf3;font-size:15px;margin:0;font-style:italic;\">\"" + escapeHtml(tip.getMessage()) + "\"</p>"
                + "</div>"
                : "";

        String logoUrl = baseUrl + "/solanatip-email-logo.png";

        String unsubscribeUrl = baseUrl + "/unsubscribe?u=" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(creator.getUsername().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;padding:0;background:#010409;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;\">"
                + "<div style=\"max-width:520px;margin:0 auto;padding:32px 20px;\">"
                // Header with logo image
                + "<div style=\"text-align:center;margin-bottom:32px;\">"
                + "<a href=\"" + baseUrl + "\" style=\"text-decoration:none;\">"
                + "<img src=\"" + logoUrl + "\" alt=\"SolanaTip\" height=\"40\" style=\"height:40px;\" />"
                + "</a>"
                + "</div>"
                // Card
                + "<div style=\"background:#0d1117;border:1px solid #1a2332;border-radius:16px;padding:32px;\">"
                + "<h2 style=\"color:#e6edf3;font-size:24px;margin:0 0 8px;text-align:center;\">You received a tip!</h2>"
                + "<p style=\"color:#8b949e;text-align:center;margin:0 0 24px;\">Someone just showed their support</p>"
                // Amount
                + "<div style=\"background:linear-gradient(135deg,#00f0ff10,#b44aff10);border:1px solid #00f0ff20;border-radius:12px;padding:24px;text-align:center;margin-bottom:20px;\">"
                + "<p style=\"color:#00f0ff;font-size:36px;font-weight:bold;margin:0;\">" + tip.getAmountSol().toPlainString() + " SOL</p>"
                + "<p style=\"color:#8b949e;font-size:14px;margin:8px 0 0;\">from <strong style=\"color:#e6edf3;\">" + escapeHtml(senderName) + "</strong></p>"
                + "</div>"
                // Message
                + message
                // Buttons
                + "<div style=\"text-align:center;margin-top:24px;\">"
                + "<a href=\"" + profileUrl + "\" style=\"display:inline-block;background:#00f0ff;color:#010409;padding:12px 28px;border-radius:10px;text-decoration:none;font-weight:bold;font-size:14px;margin-right:8px;\">View Profile</a>"
                + "<a href=\"" + explorerUrl + "\" style=\"display:inline-block;background:#1a2332;color:#8b949e;padding:12px 28px;border-radius:10px;text-decoration:none;font-size:14px;\">Verify on-chain</a>"
                + "</div>"
                + "</div>"
                // Footer
                + "<p style=\"text-align:center;color:#484f58;font-size:12px;margin-top:24px;\">"
                + "You're receiving this because someone tipped your SolanaTip page.<br>"
                + "<a href=\"" + baseUrl + "/settings\" style=\"color:#484f58;text-decoration:underline;\">Manage notifications</a>"
                + " · "
                + "<a href=\"" + unsubscribeUrl + "\" style=\"color:#484f58;text-decoration:underline;\">Unsubscribe</a>"
                + "</p>"
                + "</div></body></html>";
    }

    private String shortenWallet(String wallet) {
        if (wallet == null || wallet.length() < 10) return wallet;
        return wallet.substring(0, 6) + "..." + wallet.substring(wallet.length() - 4);
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}