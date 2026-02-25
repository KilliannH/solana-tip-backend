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
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OgImageService {

    private final TipRepository tipRepository;

    private static final int WIDTH = 1200;
    private static final int HEIGHT = 630;

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
        enableAntialiasing(g);

        Color theme = THEME_COLORS.getOrDefault(creator.getThemeColor(), THEME_COLORS.get("cyan"));
        Color bg = new Color(3, 7, 18);

        // === Full background ===
        g.setColor(bg);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Subtle grid
        g.setColor(new Color(255, 255, 255, 5));
        for (int x = 0; x < WIDTH; x += 60) g.drawLine(x, 0, x, HEIGHT);
        for (int y = 0; y < HEIGHT; y += 60) g.drawLine(0, y, WIDTH, y);

        // Glow orbs in background
        drawRadialGlow(g, 200, 150, 350, theme, 0.05f);
        drawRadialGlow(g, WIDTH - 250, HEIGHT - 150, 300, theme, 0.03f);

        // === Card ===
        int cardW = 720;
        int cardH = 460;
        int cardX = (WIDTH - cardW) / 2;
        int cardY = (HEIGHT - cardH) / 2;
        int cardRadius = 32;

        // Card shadow
        for (int i = 20; i > 0; i--) {
            g.setColor(new Color(0, 0, 0, 3));
            g.fill(new RoundRectangle2D.Float(cardX - i, cardY - i, cardW + i * 2, cardH + i * 2, cardRadius + i, cardRadius + i));
        }

        // Card background
        g.setColor(new Color(8, 12, 24, 230));
        g.fill(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, cardRadius, cardRadius));

        // Card border
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 35));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, cardRadius, cardRadius));

        // === Banner area (top of card) ===
        int bannerH = 130;
        Shape bannerClip = new RoundRectangle2D.Float(cardX, cardY, cardW, bannerH + 20, cardRadius, cardRadius);
        Shape oldClip = g.getClip();
        g.setClip(bannerClip);

        BufferedImage banner = loadImage(creator.getBannerUrl());
        if (banner != null) {
            g.drawImage(banner, cardX, cardY, cardW, bannerH, null);
        } else {
            // Gradient fallback
            GradientPaint bannerGrad = new GradientPaint(
                    cardX, cardY, new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 30),
                    cardX + cardW, cardY + bannerH, new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 6)
            );
            g.setPaint(bannerGrad);
            g.fillRect(cardX, cardY, cardW, bannerH);
        }

        // Banner fade to card bg
        GradientPaint fade = new GradientPaint(
                cardX, cardY + bannerH - 60, new Color(8, 12, 24, 0),
                cardX, cardY + bannerH, new Color(8, 12, 24, 230)
        );
        g.setPaint(fade);
        g.fillRect(cardX, cardY + bannerH - 60, cardW, 60);
        g.setClip(oldClip);

        // === Avatar ===
        int avatarSize = 72;
        int avatarX = cardX + 48;
        int avatarY = cardY + bannerH - 20;

        // Avatar border glow
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 40));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new RoundRectangle2D.Float(avatarX - 2, avatarY - 2, avatarSize + 4, avatarSize + 4, 18, 18));

        BufferedImage avatar = loadImage(creator.getAvatarUrl());
        if (avatar != null) {
            drawRoundedImage(g, avatar, avatarX, avatarY, avatarSize, 16);
        } else {
            drawInitials(g, creator, avatarX, avatarY, avatarSize, theme);
        }

        // === Name + username ===
        int textX = avatarX + avatarSize + 18;
        int nameY = avatarY + 30;

        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.setColor(Color.WHITE);
        String displayName = truncate(creator.getDisplayName(), 24);
        g.drawString(displayName, textX, nameY);

        // Pro badge inline
        if (creator.getSubscriptionPlan() == SubscriptionPlan.PRO) {
            FontMetrics fm = g.getFontMetrics();
            int badgeX = textX + fm.stringWidth(displayName) + 10;
            int badgeY = nameY - 18;
            drawProBadge(g, badgeX, badgeY, theme);
        }

        g.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g.setColor(new Color(255, 255, 255, 60));
        g.drawString("@" + creator.getUsername(), textX, nameY + 22);

        // === Bio (if exists) ===
        int contentY = avatarY + avatarSize + 24;
        if (creator.getBio() != null && !creator.getBio().isBlank()) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g.setColor(new Color(255, 255, 255, 70));
            String bio = truncate(creator.getBio(), 80);
            drawWrappedText(g, bio, cardX + 48, contentY, cardW - 96, 22);
            contentY += 52;
        }

        // === Stats row ===
        int statsY = contentY + 8;
        BigDecimal totalTips = tipRepository.sumAmountByCreatorIdAndStatus(creator.getId(), TipStatus.CONFIRMED);
        long tipCount = tipRepository.countByCreatorIdAndStatus(creator.getId(), TipStatus.CONFIRMED);
        String solStr = (totalTips != null ? totalTips.setScale(2, RoundingMode.HALF_UP).toString() : "0.00");

        // SOL stat
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        g.drawString(solStr, cardX + 48, statsY + 20);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 160));
        FontMetrics fm = g.getFontMetrics(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("SOL received", cardX + 48 + fm.stringWidth(solStr) + 8, statsY + 20);

        // Divider dot
        int dotX = cardX + 48 + fm.stringWidth(solStr) + 8 + g.getFontMetrics().stringWidth("SOL received") + 16;
        g.setColor(new Color(255, 255, 255, 20));
        g.fillOval(dotX, statsY + 14, 4, 4);

        // Tips count
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        g.drawString(String.valueOf(tipCount), dotX + 20, statsY + 20);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(255, 100, 180, 140));
        fm = g.getFontMetrics(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("tips", dotX + 20 + fm.stringWidth(String.valueOf(tipCount)) + 6, statsY + 20);

        // === "Tip with SOL" button ===
        int btnW = cardW - 96;
        int btnH = 48;
        int btnX = cardX + 48;
        int btnY = cardY + cardH - 48 - 40;

        // Button glow
        for (int i = 12; i > 0; i--) {
            g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 2));
            g.fill(new RoundRectangle2D.Float(btnX - i, btnY - i, btnW + i * 2, btnH + i * 2, 16 + i, 16 + i));
        }

        // Button gradient fill
        GradientPaint btnGrad = new GradientPaint(
                btnX, btnY, theme,
                btnX + btnW, btnY + btnH, new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 200)
        );
        g.setPaint(btnGrad);
        g.fill(new RoundRectangle2D.Float(btnX, btnY, btnW, btnH, 16, 16));

        // Button text
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(new Color(3, 7, 18));
        String btnText = "⚡  TIP WITH SOL";
        fm = g.getFontMetrics();
        int textW = fm.stringWidth(btnText);
        g.drawString(btnText, btnX + (btnW - textW) / 2, btnY + 31);

        // === "powered by SolanaTip" footer ===
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(255, 255, 255, 25));
        String powered = "powered by SolanaTip";
        fm = g.getFontMetrics();
        g.drawString(powered, cardX + (cardW - fm.stringWidth(powered)) / 2, cardY + cardH - 16);

        // === Top & bottom accent lines ===
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 30));
        g.fillRect(0, 0, WIDTH, 2);
        g.fillRect(0, HEIGHT - 2, WIDTH, 2);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    // === Helpers ===

    private void enableAntialiasing(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private void drawRadialGlow(Graphics2D g, int cx, int cy, int radius, Color color, float maxAlpha) {
        for (int i = radius; i > 0; i -= 5) {
            float alpha = maxAlpha * ((float) i / radius);
            int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), a));
            g.fillOval(cx - i / 2, cy - i / 2, i, i);
        }
    }

    private void drawRoundedImage(Graphics2D g, BufferedImage img, int x, int y, int size, int radius) {
        BufferedImage rounded = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = rounded.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new RoundRectangle2D.Float(0, 0, size, size, radius, radius));
        g2.drawImage(img, 0, 0, size, size, null);
        g2.dispose();
        g.drawImage(rounded, x, y, null);
    }

    private void drawInitials(Graphics2D g, Creator creator, int x, int y, int size, Color theme) {
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 20));
        g.fill(new RoundRectangle2D.Float(x, y, size, size, 16, 16));

        String initial = creator.getDisplayName().substring(0, 1).toUpperCase();
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 200));
        FontMetrics fm = g.getFontMetrics();
        int tx = x + (size - fm.stringWidth(initial)) / 2;
        int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(initial, tx, ty);
    }

    private void drawProBadge(Graphics2D g, int x, int y, Color theme) {
        int w = 52, h = 22, r = 12;
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 20));
        g.fill(new RoundRectangle2D.Float(x, y, w, h, r, r));
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 50));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, r, r));

        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        g.setColor(new Color(theme.getRed(), theme.getGreen(), theme.getBlue(), 200));
        g.drawString("★ PRO", x + 9, y + 15);
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int currentY = y;
        int lines = 0;

        for (String word : text.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(test) > maxWidth) {
                if (lines >= 1) {
                    g.drawString(line + "...", x, currentY);
                    return;
                }
                g.drawString(line.toString(), x, currentY);
                line = new StringBuilder(word);
                currentY += lineHeight;
                lines++;
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) g.drawString(line.toString(), x, currentY);
    }

    private BufferedImage loadImage(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            return ImageIO.read(URI.create(url).toURL());
        } catch (Exception e) {
            log.debug("Could not load image from {}: {}", url, e.getMessage());
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}