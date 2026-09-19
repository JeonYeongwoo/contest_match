package com.contestmate.collector;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Fetches an RSS/Atom feed and turns each entry into a CollectedItem. */
@Component
public class RssCollector implements Collector {

    @Override
    public boolean supports(SourceType type) {
        return type == SourceType.RSS;
    }

    @Override
    public List<CollectedItem> collect(Source source) throws Exception {
        List<CollectedItem> items = new ArrayList<>();
        SyndFeedInput input = new SyndFeedInput();
        try (XmlReader reader = new XmlReader(new URL(source.getBaseUrl()))) {
            SyndFeed feed = input.build(reader);
            for (SyndEntry entry : feed.getEntries()) {
                String html = entry.getDescription() != null ? entry.getDescription().getValue() : "";
                String text = Jsoup.parse(html).text();
                items.add(new CollectedItem(entry.getLink(), entry.getTitle(), text));
            }
        }
        return items;
    }
}
