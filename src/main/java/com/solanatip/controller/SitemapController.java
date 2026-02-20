package com.solanatip.controller;

import com.solanatip.entity.Creator;
import com.solanatip.repository.CreatorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    private final CreatorRepository creatorRepository;

    @Value("${app.base-url:https://solana-tip.com}")
    private String baseUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        List<Creator> creators = creatorRepository.findAll();

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static pages
        sb.append(url(baseUrl, "1.0", "weekly"));
        sb.append(url(baseUrl + "/creators", "0.9", "daily"));
        sb.append(url(baseUrl + "/terms", "0.3", "monthly"));
        sb.append(url(baseUrl + "/privacy", "0.3", "monthly"));
        sb.append(url(baseUrl + "/cookies", "0.3", "monthly"));

        // Creator pages
        for (Creator creator : creators) {
            sb.append(url(baseUrl + "/creator/" + creator.getUsername(), "0.7", "weekly"));
        }

        sb.append("</urlset>");
        return sb.toString();
    }

    private String url(String loc, String priority, String changefreq) {
        return "  <url>\n" +
                "    <loc>" + loc + "</loc>\n" +
                "    <priority>" + priority + "</priority>\n" +
                "    <changefreq>" + changefreq + "</changefreq>\n" +
                "  </url>\n";
    }
}