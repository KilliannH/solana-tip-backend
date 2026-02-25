package com.solanatip.service;

import com.solanatip.entity.Creator;
import com.solanatip.entity.SubscriptionPlan;
import com.solanatip.entity.TipStatus;
import com.solanatip.repository.TipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OgImageService {

    private final TipRepository tipRepository;

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 630;

    // Theme color map matching frontend
    private static final Map<String, Color> THEME_COLORS = Map.of(
            "cyan", new Color(0, 240, 255),
            "purple", new Color(180, 74, 255),
            "pink", new Color(255, 46, 170),
            "green", new Color(57, 255, 20),
            "blue", new Color(77, 124, 255)
    );

    public byte[] generateOgImage(Creator creator) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Enable anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        Color themeColor = THEME_COLORS.getOrDefault(creator.getThemeColor(), THEME_COLORS.get("cyan"));
        Color bgColor = new Color(3, 7, 18);         // void-950
        Color bgLight = new Color(10, 15, 30);
        Color textWhite = new Color(255, 255, 255);
        Color textMuted = new Color(255, 255, 255, 80);
        Color textDim = new Color(255, 255, 255, 40);

        // === Background ===
        g.setColor(bgColor);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Grid pattern (subtle)
        g.setColor(new Color(255, 255, 255, 6));
        for (int x = 0; x < WIDTH; x += 60) {
            g.drawLine(x, 0, x, HEIGHT);
        }
        for (int y = 0; y < HEIGHT; y += 60) {
            g.drawLine(0, y, WIDTH, y);
        }

        // Theme color glow orb (top right)
        drawGlowOrb(g, WIDTH - 150, -50, 400, themeColor, 0.06f);

        // Secondary glow orb (bottom left)
        drawGlowOrb(g, -100, HEIGHT - 100, 300, themeColor, 0.04f);

        // === Content area ===
        int leftMargin = 80;
        int contentTop = 80;

        // --- "Creator" label ---
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 150));
        g.drawString("CREATOR", leftMargin, contentTop);

        // --- Avatar circle ---
        int avatarSize = 90;
        int avatarX = leftMargin;
        int avatarY = contentTop + 20;

        BufferedImage avatar = loadAvatar(creator.getAvatarUrl());
        if (avatar != null) {
            drawRoundedAvatar(g, avatar, avatarX, avatarY, avatarSize);
        } else {
            // Initials fallback
            drawInitialsAvatar(g, creator, avatarX, avatarY, avatarSize, themeColor);
        }

        // --- Display name ---
        int textX = avatarX + avatarSize + 24;
        int nameY = avatarY + 30;
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        g.setColor(textWhite);

        String displayName = truncate(creator.getDisplayName(), 22);
        g.drawString(displayName, textX, nameY);

        // --- Pro badge (if applicable) ---
        if (creator.getSubscriptionPlan() == SubscriptionPlan.PRO) {
            FontMetrics fm = g.getFontMetrics();
            int nameWidth = fm.stringWidth(displayName);
            int badgeX = textX + nameWidth + 12;
            int badgeY = nameY - 22;
            drawProBadge(g, badgeX, badgeY, themeColor);
        }

        // --- @username ---
        g.setFont(new Font("Monospaced", Font.PLAIN, 16));
        g.setColor(textDim);
        g.drawString("@" + creator.getUsername(), textX, nameY + 28);

        // --- Bio ---
        if (creator.getBio() != null && !creator.getBio().isBlank()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            g.setColor(textMuted);
            String bio = truncate(creator.getBio(), 90);
            drawWrappedText(g, bio, leftMargin, avatarY + avatarSize + 40, WIDTH - leftMargin * 2, 26);
        }

        // === Stats bar at bottom ===
        int statsY = HEIGHT - 160;

        // Stats background bar
        g.setColor(new Color(255, 255, 255, 4));
        g.fill(new RoundRectangle2D.Float(leftMargin, statsY, WIDTH - leftMargin * 2, 70, 20, 20));
        g.setColor(new Color(255, 255, 255, 8));
        g.draw(new RoundRectangle2D.Float(leftMargin, statsY, WIDTH - leftMargin * 2, 70, 20, 20));

        // Tips received
        BigDecimal totalTips = tipRepository.sumAmountByCreatorIdAndStatus(creator.getId(), TipStatus.CONFIRMED);
        long tipCount = tipRepository.countByCreatorIdAndStatus(creator.getId(), TipStatus.CONFIRMED);

        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(textWhite);
        String solAmount = (totalTips != null ? totalTips.setScale(2, java.math.RoundingMode.HALF_UP).toString() : "0.00");
        g.drawString(solAmount, leftMargin + 24, statsY + 42);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 180));
        FontMetrics fm = g.getFontMetrics(new Font("SansSerif", Font.BOLD, 22));
        int solTextEnd = leftMargin + 24 + fm.stringWidth(solAmount) + 6;
        g.drawString("SOL received", solTextEnd, statsY + 42);

        // Divider
        int divX = leftMargin + 300;
        g.setColor(new Color(255, 255, 255, 10));
        g.drawLine(divX, statsY + 15, divX, statsY + 55);

        // Tip count
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(textWhite);
        g.drawString(String.valueOf(tipCount), divX + 24, statsY + 42);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(new Color(255, 100, 180, 150));
        fm = g.getFontMetrics(new Font("SansSerif", Font.BOLD, 22));
        int countEnd = divX + 24 + fm.stringWidth(String.valueOf(tipCount)) + 6;
        g.drawString("tips", countEnd, statsY + 42);

        // === Branding (bottom right) ===
        int brandY = HEIGHT - 55;
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(new Color(255, 255, 255, 50));
        String brand = "solana-tip.com";
        fm = g.getFontMetrics();
        g.drawString(brand, WIDTH - leftMargin - fm.stringWidth(brand), brandY);

        // Solana bolt icon (simple)
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 60));
        int boltX = WIDTH - leftMargin - fm.stringWidth(brand) - 24;
        g.setFont(new Font("SansSerif", Font.BOLD, 18));
        g.drawString("⚡", boltX, brandY);

        // === Border accent (top & bottom lines) ===
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 40));
        g.fillRect(0, 0, WIDTH, 3);
        g.fillRect(0, HEIGHT - 3, WIDTH, 3);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private void drawGlowOrb(Graphics2D g, int cx, int cy, int radius, Color color, float alpha) {
        for (int i = radius; i > 0; i -= 4) {
            float a = alpha * (1.0f - (float) (radius - i) / radius);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, (int) (a * 255)))));
            g.fillOval(cx - i / 2, cy - i / 2, i, i);
        }
    }

    private void drawRoundedAvatar(Graphics2D g, BufferedImage avatar, int x, int y, int size) {
        BufferedImage rounded = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = rounded.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new RoundRectangle2D.Float(0, 0, size, size, 20, 20));
        g2.drawImage(avatar, 0, 0, size, size, null);
        g2.dispose();
        g.drawImage(rounded, x, y, null);
    }

    private void drawInitialsAvatar(Graphics2D g, Creator creator, int x, int y, int size, Color themeColor) {
        // Background
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 30));
        g.fill(new RoundRectangle2D.Float(x, y, size, size, 20, 20));
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 50));
        g.draw(new RoundRectangle2D.Float(x, y, size, size, 20, 20));

        // Initials
        String initials = creator.getDisplayName().split(" ")[0].substring(0, Math.min(2, creator.getDisplayName().length())).toUpperCase();
        g.setFont(new Font("SansSerif", Font.BOLD, 30));
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 200));
        FontMetrics fm = g.getFontMetrics();
        int textX = x + (size - fm.stringWidth(initials)) / 2;
        int textY = y + (size + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(initials, textX, textY);
    }

    private void drawProBadge(Graphics2D g, int x, int y, Color themeColor) {
        int badgeWidth = 52;
        int badgeHeight = 22;
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 25));
        g.fill(new RoundRectangle2D.Float(x, y, badgeWidth, badgeHeight, 12, 12));
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 60));
        g.draw(new RoundRectangle2D.Float(x, y, badgeWidth, badgeHeight, 12, 12));

        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), 200));
        g.drawString("★ PRO", x + 8, y + 15);
    }

    private BufferedImage loadAvatar(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return ImageIO.read(URI.create(url).toURL());
        } catch (Exception e) {
            log.debug("Could not load avatar from {}: {}", url, e.getMessage());
            return null;
        }
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int currentY = y;
        int maxLines = 2;
        int lineCount = 0;

        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(test) > maxWidth) {
                g.drawString(line.toString(), x, currentY);
                line = new StringBuilder(word);
                currentY += lineHeight;
                lineCount++;
                if (lineCount >= maxLines) {
                    // Add ellipsis to last line if needed
                    g.drawString(line + "...", x, currentY);
                    return;
                }
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) {
            g.drawString(line.toString(), x, currentY);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}